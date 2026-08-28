package no.nav.su.se.bakover.service.historisk

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.client.historisk.SupstonadHistoriskClient
import no.nav.su.se.bakover.client.historisk.UttrekkResponse
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskImportOversikt
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.domain.historisk.SlettImportResultat
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Leser fra supstonad-historisk og kan kopiere de avtalte tabellene til et lokalt, tapsfritt rådatasnapshot.
 * Rådataene projiseres ikke til dagens vedtaksmodell her.
 */
class SupstonadHistoriskService(
    private val supstonadHistoriskClient: SupstonadHistoriskClient,
    private val historiskImportRepo: HistoriskImportRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun tellRader(tabellnavn: String): Either<ClientError, CountResponse> {
        log.info("SupstonadHistoriskService: Starter tellRader for tabell '{}'", tabellnavn)
        return supstonadHistoriskClient.tellRader(tabellnavn).also { resultat ->
            resultat.fold(
                ifLeft = { feil ->
                    log.error(
                        "SupstonadHistoriskService: tellRader feilet for tabell '{}': {}",
                        tabellnavn,
                        feil,
                    )
                },
                ifRight = { svar ->
                    log.info(
                        "SupstonadHistoriskService: tellRader for tabell '{}' fullført med antall {}",
                        tabellnavn,
                        svar.antall,
                    )
                },
            )
        }
    }

    fun hentAlleImporter(): List<HistoriskImportOversikt> = historiskImportRepo.hentAlleImporter()

    fun slettImport(importId: UUID): Either<KunneIkkeSletteImport, Unit> {
        log.info("Historisk import: sletter import {}", importId)
        return when (historiskImportRepo.slettImport(importId)) {
            SlettImportResultat.SLETTET -> {
                log.info("Historisk import: import {} slettet", importId)
                Unit.right()
            }
            SlettImportResultat.IKKE_FUNNET -> KunneIkkeSletteImport.IkkeFunnet.left()
            SlettImportResultat.PÅGÅR -> KunneIkkeSletteImport.ImportPågår.left()
        }
    }

    fun hentUttrekk(
        tabellnavn: String,
        antallRader: Long,
        iterator: String? = null,
    ): Either<ClientError, UttrekkResponse> {
        log.info("SupstonadHistoriskService: Starter hentUttrekk for tabell '{}'", tabellnavn)
        return supstonadHistoriskClient.hentUttrekk(
            tabellnavn = tabellnavn,
            antallRader = antallRader,
            iterator = iterator,
        ).also { resultat ->
            resultat.fold(
                ifLeft = { feil ->
                    log.error(
                        "SupstonadHistoriskService: hentUttrekk feilet for tabell '{}': {}",
                        tabellnavn,
                        feil,
                    )
                },
                ifRight = { svar ->
                    log.info(
                        "SupstonadHistoriskService: hentUttrekk for tabell '{}' fullført med {} rader",
                        tabellnavn,
                        svar.innhold.size,
                    )
                },
            )
        }
    }

    /**
     * Kopierer alle avtalte Infotrygd-tabeller til et tapsfritt rådatasnapshot.
     *
     * I første omgang støtter vi ikke gjenopptakelse: enhver feil underveis (klientfeil, skjema-/antallsavvik eller
     * uventet exception) markerer hele importen som FEILET. En ny import må da startes fra bunnen.
     * Kun én import kan pågå om gangen; nye kall avvises med [KunneIkkeImportereHistoriskeData.ImportPågår].
     * Metoden er ment kalt fra en langvarig bakgrunnsjobb, ikke direkte i en HTTP-request.
     *
     *
     * Flyt:
     *  importerAlleTabeller
     *    └─ for hver tabell:
     *         importerTabell        ← paginerer denne ene tabellen
     *           while PÅGÅR:
     *             hentUttrekk(iterator)   → én side (maks sideStørrelse rader) + ny iterator
     *             validerSide(...)        → feil eller ok
     *             lagreSide(...)          → skriver JSONB-rader + flytter checkpoint
     */
    fun importerAlleTabeller(
        sideStørrelse: Long = STANDARD_ANTALL_RADER_PER_SIDE,
        midlertidigUtenValidering: Boolean = false,
    ): Either<KunneIkkeImportereHistoriskeData, HistoriskImportresultat> {
        if (sideStørrelse !in 1..MAKS_ANTALL_RADER_PER_SIDE) {
            return KunneIkkeImportereHistoriskeData.UgyldigSidestørrelse(
                antallRaderPerSide = sideStørrelse,
                maksAntallRaderPerSide = MAKS_ANTALL_RADER_PER_SIDE,
            ).left()
        }

        val eksisterende = historiskImportRepo.hentPågåendeImport()
        if (eksisterende != null) {
            log.info("Historisk import: avviser ny import, {} pågår siden {}", eksisterende.id, eksisterende.opprettet)
            return KunneIkkeImportereHistoriskeData.ImportPågår(eksisterende.id, eksisterende.opprettet).left()
        }

        log.info("Historisk import: henter og validerer kildeskjema")
        val kildeSkjema = validerOgHentKildeSkjema(midlertidigUtenValidering).getOrElse { return it.left() }
        log.info("Historisk import: kildeskjema OK, {} tabeller", kildeSkjema.size)

        log.info("Historisk import: oppretter ny import")
        val import = opprettImport(kildeSkjema).getOrElse { return it.left() }.also {
            log.info("Historisk import: opprettet import {} med {} tabeller", it.id, it.tabeller.size)
        }

        try {
            import.tabeller.forEach { lagretTabell ->
                importerTabell(import, lagretTabell, sideStørrelse)
                    ?.let { return markerFeilet(import, it) }
            }
            historiskImportRepo.fullførImport(import.id)
        } catch (e: Exception) {
            log.error("Historisk import {} feilet uventet og markeres som feilet", import.id, e)
            return markerFeilet(import, KunneIkkeImportereHistoriskeData.UventetFeil)
        }

        val resultat = HistoriskImportresultat(
            importId = import.id,
            importerteRader = import.tabeller.sumOf { it.forventetAntall },
            importerteTabeller = import.tabeller.size,
        )
        log.info(
            "Historisk import {} fullført: {} tabeller, {} rader totalt",
            resultat.importId,
            resultat.importerteTabeller,
            resultat.importerteRader,
        )
        return resultat.right()
    }

    private fun validerOgHentKildeSkjema(
        midlertidigUtenValidering: Boolean = false,
    ): Either<KunneIkkeImportereHistoriskeData, Map<String, List<String>>> {
        if (midlertidigUtenValidering) {
            // TODO midlertidig hardkoding inntil endepunkt for tabeller er implementert historisk-exodus-supstonad
            return TABELLER_MED_KOLONNER.right()
        }
        val kildeSkjema = supstonadHistoriskClient.hentTabeller().getOrElse {
            return KunneIkkeImportereHistoriskeData.Klientfeil("hentTabeller", it).left()
        }
        val manglendeTabeller = TABELLER_SOM_SKAL_IMPORTERES - kildeSkjema.keys
        if (manglendeTabeller.isNotEmpty()) {
            return KunneIkkeImportereHistoriskeData.ManglendeTabeller(manglendeTabeller).left()
        }
        TABELLER_SOM_SKAL_IMPORTERES.forEach { tabellnavn ->
            val kolonner = kildeSkjema.getValue(tabellnavn)
            when {
                kolonner.isEmpty() ->
                    return KunneIkkeImportereHistoriskeData.UgyldigSkjema(tabellnavn, "Tabellen mangler kolonner").left()
                kolonner.any { it.isBlank() } ->
                    return KunneIkkeImportereHistoriskeData.UgyldigSkjema(tabellnavn, "Tabellen har blankt kolonnenavn").left()
                kolonner.distinct().size != kolonner.size ->
                    return KunneIkkeImportereHistoriskeData.UgyldigSkjema(tabellnavn, "Tabellen har duplikate kolonnenavn").left()
            }
        }
        return kildeSkjema.right()
    }

    private fun importerTabell(
        import: HistoriskImport,
        startTabell: HistoriskImport.Tabell,
        sideStørrelse: Long,
    ): KunneIkkeImportereHistoriskeData? {
        var tabell = startTabell
        val bruktIteratorer = mutableSetOf<String>().apply { tabell.nesteIterator?.let { add(it) } }
        while (tabell.status == HistoriskImport.Status.PÅGÅR) {
            val uttrekk = supstonadHistoriskClient.hentUttrekk(
                tabellnavn = tabell.tabellnavn,
                antallRader = sideStørrelse,
                iterator = tabell.nesteIterator,
            ).getOrElse { return KunneIkkeImportereHistoriskeData.Klientfeil("hentUttrekk", it) }

            val varSisteSide = uttrekk.iterator.isNotBlank() && !bruktIteratorer.add(uttrekk.iterator)
            if (varSisteSide) {
                // Hvis samme iterator kommer igjen tyder det på at det ikke er mer å hente
                break
            }
            validerSide(tabell, uttrekk)?.let { return it }

            tabell = historiskImportRepo.lagreSide(
                HistoriskRådataSide(
                    importId = import.id,
                    tabellnavn = tabell.tabellnavn,
                    side = tabell.nesteSide,
                    nesteIterator = uttrekk.iterator.takeUnless { it.isBlank() },
                    rader = uttrekk.innhold.map { tabell.kolonner.zip(it).toMap() },
                ),
            )
        }
        val totaltImportert = tabell.importertAntall
        if (totaltImportert > tabell.forventetAntall || totaltImportert != tabell.forventetAntall) {
            return KunneIkkeImportereHistoriskeData.Antallsavvik(
                tabellnavn = tabell.tabellnavn,
                forventet = tabell.forventetAntall,
                faktisk = totaltImportert,
            )
        }
        return null
    }

    private fun validerSide(
        tabell: HistoriskImport.Tabell,
        uttrekk: UttrekkResponse,
    ): KunneIkkeImportereHistoriskeData? {
        val uttrekkKolonner = uttrekk.schema.kolonner.map { it.navn }
        if (uttrekkKolonner.toSet() != tabell.kolonner.toSet()) {
            val ulike = kolonneDiff(tabell.kolonner, uttrekkKolonner)
            return KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                tabell.tabellnavn,
                "Skjema i uttrekk er ikke lik skjemaet som importen ble startet med. Ulike kolonner: $ulike",
            )
        }
        uttrekk.innhold.forEachIndexed { radnummer, kolonneverdier ->
            if (kolonneverdier.size != tabell.kolonner.size) {
                return KunneIkkeImportereHistoriskeData.UgyldigRadbredde(
                    tabellnavn = tabell.tabellnavn,
                    side = tabell.nesteSide,
                    radnummer = radnummer,
                    forventet = tabell.kolonner.size,
                    faktisk = kolonneverdier.size,
                )
            }
        }
        return null
    }

    private fun kolonneDiff(forventet: List<String>, faktisk: List<String>): String {
        val maxLengde = if (forventet.size >= faktisk.size) forventet.size else faktisk.size
        val avvik = (0 until maxLengde).mapNotNull { i ->
            val f = forventet.getOrNull(i)
            val a = faktisk.getOrNull(i)
            when {
                f == a -> null
                f == null -> "pos $i: mangler i forventet (faktisk=$a)"
                a == null -> "pos $i: mangler i faktisk (forventet=$f)"
                else -> "pos $i: forventet=$f, faktisk=$a"
            }
        }
        return avvik.joinToString("; ")
    }

    private fun opprettImport(
        kildeSkjema: Map<String, List<String>>,
    ): Either<KunneIkkeImportereHistoriskeData, HistoriskImport> {
        val tabeller = TABELLER_SOM_SKAL_IMPORTERES.sorted().map { tabellnavn ->
            val antall = supstonadHistoriskClient.tellRader(tabellnavn).getOrElse {
                return KunneIkkeImportereHistoriskeData.Klientfeil("tellRader", it).left()
            }
            NyHistoriskTabellimport(
                tabellnavn = tabellnavn,
                forventetAntall = antall.antall,
                kolonner = kildeSkjema.getValue(tabellnavn),
            )
        }
        return historiskImportRepo.opprettImport(tabeller).right()
    }

    private fun markerFeilet(
        import: HistoriskImport,
        feil: KunneIkkeImportereHistoriskeData,
    ): Either<KunneIkkeImportereHistoriskeData, Nothing> {
        // Feilteksten inneholder kun tabell-/posisjonsmetadata, aldri rådata.
        historiskImportRepo.markerFeilet(import.id, feil.toString())
        log.warn("Historisk import {} ble markert som feilet: {}", import.id, feil)
        return feil.left()
    }

    companion object {
        const val STANDARD_ANTALL_RADER_PER_SIDE = 1_000L
        const val MAKS_ANTALL_RADER_PER_SIDE = 10_000L

        val TABELLER_SOM_SKAL_IMPORTERES = setOf(
            InfotrygdTabeller.T_BELOPSTYPE,
            InfotrygdTabeller.T_BEREGN_FAKTOR,
            InfotrygdTabeller.T_BEREGN_GRL,
            InfotrygdTabeller.T_BESLUT,
            InfotrygdTabeller.T_DELYTELSE,
            InfotrygdTabeller.T_DELYTELSESTYPE,
            InfotrygdTabeller.T_ENDRING,
            InfotrygdTabeller.T_KJOREPLAN_AVST,
            InfotrygdTabeller.T_KLASSENIVAA,
            InfotrygdTabeller.T_LOPENR_FNR,
            InfotrygdTabeller.T_MAP_DELYTELSE,
            InfotrygdTabeller.T_ROLLE,
            InfotrygdTabeller.T_STONAD,
            InfotrygdTabeller.T_STONADSKLASSE,
            InfotrygdTabeller.T_SU,
            InfotrygdTabeller.T_VEDTAK,
        )

        val TABELLER_MED_KOLONNER = mapOf(
            InfotrygdTabeller.T_STONAD to listOf(
                "STONAD_ID", "PERSON_LOPENR", "KODE_RUTINE", "DATO_START", "KODE_OPPHOR", "DATO_OPPHOR", "OPPDRAG_ID",
                "TIDSPUNKT_OPPHORT", "TIDSPUNKT_REG", "BRUKERID", "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
            InfotrygdTabeller.T_VEDTAK to listOf(
                "VEDTAK_ID", "PERSON_LOPENR", "KODE_RUTINE", "DATO_START", "TKNR", "SAKSBLOKK", "SAKSNR", "TYPE_SAK",
                "KODE_RESULTAT", "DATO_INNV_FOM", "DATO_INNV_TOM", "DATO_MOTTATT_SAK", "KODE_VEDTAKSNIVAA",
                "TYPE_BEREGNING", "TKNR_BEH", "TIDSPUNKT_REG", "BRUKERID", "NOKKEL_DL1", "ALTERNATIV_MOTTAKER",
                "STONAD_ID", "KIDNR", "FAKTNR", "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
            InfotrygdTabeller.T_LOPENR_FNR to listOf("PERSON_LOPENR", "PERSONNR", "OPPRETTET", "OPPDATERT", "DB_SPLITT"),
            InfotrygdTabeller.T_DELYTELSE to listOf(
                "VEDTAK_ID", "TYPE_DELYTELSE", "TIDSPUNKT_REG", "FOM", "TOM", "BELOP", "OPPGJORSORDNING",
                "MOTTAKER_LOPENR", "BRUKERID", "TYPE_SATS", "TYPE_UTBETALING", "LINJE_ID",
                "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
            InfotrygdTabeller.T_BELOPSTYPE to listOf("TYPE", "TEKST", "BEHANDLING", "OPPRETTET", "OPPDATERT", "DB_SPLITT"),
            InfotrygdTabeller.T_DELYTELSESTYPE to listOf(
                "TYPE",
                "TEKST",
                "FRADRAG_TILLEGG",
                "SEKVENSNR",
                "OPPRETTET",
                "OPPDATERT",
                "DB_SPLITT",
            ),
            InfotrygdTabeller.T_KLASSENIVAA to listOf("KODE", "TEKST", "OPPRETTET", "OPPDATERT"),
            InfotrygdTabeller.T_ROLLE to listOf(
                "VEDTAK_ID", "TYPE", "TIDSPUNKT_REG", "FOM", "TOM", "PERSON_LOPENR_R", "BRUKERID", "BARN_TYPE",
                "BOR_SAMMEN_MED", "TRYGDETID_FAKTISK", "TRYGDETID_ANVENDT", "TRYGDETID_UNNTAK",
                "TRYGD_MEDLEM_SIDEN", "UTENLANDSOPPHOLD", "BT_1_SUM", "BT_1_ANTALL", "BT_2_SUM", "BT_2_ANTALL",
                "BT_S_SUM", "BT_S_ANTALL", "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
            InfotrygdTabeller.T_BESLUT to listOf(
                "BESLUTNING_ID", "VEDTAK_ID", "SAKSBEHANDLER1", "GODKJENT1", "ENHET1", "TIDSPUNKT_REG1",
                "SAKSBEHANDLER2", "GODKJENT2", "ENHET2", "TIDSPUNKT_REG2", "SENDT_TIL_OS", "MOTTATT_FRA_OS",
                "GODKJENT_AV_OS", "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
            InfotrygdTabeller.T_ENDRING to listOf("VEDTAK_ID", "KODE", "OPPRETTET", "OPPDATERT", "DB_SPLITT"),
            InfotrygdTabeller.T_SU to listOf(
                "VEDTAK_ID", "TIDSPUNKT_REG", "BELOP_BER_GRUNNLAG", "BRUKERID", "REVURDERING_DATO",
                "OPPRETTET", "OPPDATERT", "DB_SPLITT", "ENDRET_I_KILDE",
            ),
            InfotrygdTabeller.T_STONADSKLASSE to listOf(
                "VEDTAK_ID",
                "KODE_NIVAA",
                "KODE_KLASSE",
                "OPPRETTET",
                "OPPDATERT",
                "DB_SPLITT",
            ),
            InfotrygdTabeller.T_BEREGN_GRL to listOf(
                "VEDTAK_ID", "TYPE_BELOP", "TIDSPUNKT_REG", "FOM", "TOM", "BELOP", "BRUKERID",
                "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
            InfotrygdTabeller.T_BEREGN_FAKTOR to listOf(
                "EF_OG_RED_PROSENT",
                "OPPRETTET",
                "OPPDATERT",
                "DB_SPLITT",
                "ID_BEREGN_FAKTOR",
                "TILL_BARN_PROS",
                "KODE_RUTINE_UNDER",
                "KODE_SATS_UNDER",
                "SATS_UNDER",
                "EF_FAKTOR",
                "EF_PROSENT",
                "MPN_HOY_SATS_100",
                "MPN_HOY_SATS_125",
                "MPN_ORD_SATS_100",
                "VIRKFOM",
                "IVERKSETT",
                "MINYTELSE",
                "MINUYTELS",
                "MAXINNT",
                "MAXPROS",
                "DAGPROS",
                "ANTDAGER",
                "BSATS",
                "AGRENSE",
                "YTELSE_SU",
                "YTELSE_SU_EKT_U_67",
                "YTELSE_SU_EKT_O_67",
                "MINPROS",
                "KODE_RUTINE",
            ),
            InfotrygdTabeller.T_KJOREPLAN_AVST to listOf(
                "KODE_RUTINE", "TYPE_AVSTEMMING", "AVSTEM_FREKVENS", "METODE", "BRUKERID", "TIDSPUNKT_REG",
                "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
            InfotrygdTabeller.T_MAP_DELYTELSE to listOf(
                "TYPE_DELYTELSE", "KODE_RUTINE", "KODE_RUTINE_REF", "KODE_FAGOMR", "KODE_FAGOMR_REF", "BRUKERID",
                "TIDSPUNKT_REG", "OPPRETTET", "OPPDATERT", "DB_SPLITT",
            ),
        )
    }
}

data class HistoriskImportresultat(
    val importId: UUID,
    val importerteRader: Long,
    val importerteTabeller: Int,
)

/**
 * Seeder databasen med faste historiske importer for lokal utvikling.
 * Kalles ved oppstart kun lokalt slik at frontend alltid har data å jobbe med.
 * Sletting fungerer normalt i frontend, men dataen kommer tilbake ved neste restart.
 */
fun seedHistoriskeImporterLokalt(historiskImportRepo: HistoriskImportRepo) {
    // Marker evt. pågående import som feilet (kan ikke slettes direkte) og slett alt
    historiskImportRepo.hentPågåendeImport()?.let { historiskImportRepo.markerFeilet(it.id, "Seed reset ved oppstart") }
    historiskImportRepo.hentAlleImporter().forEach { historiskImportRepo.slettImport(it.id) }

    val tabellInfo = listOf(
        Triple(InfotrygdTabeller.T_STONAD, 3L, listOf("STONAD_ID", "PERSON_LOPENR", "DATO_START", "KODE_OPPHOR", "DATO_OPPHOR", "OPPDRAG_ID")),
        Triple(InfotrygdTabeller.T_VEDTAK, 5L, listOf("VEDTAK_ID", "STONAD_ID", "KODE_RESULTAT", "DATO_INNV_FOM", "DATO_INNV_TOM", "TKNR", "SAKSNR", "SAKSBLOKK")),
        Triple(InfotrygdTabeller.T_LOPENR_FNR, 3L, listOf("PERSON_LOPENR", "PERSONNR")),
        Triple(InfotrygdTabeller.T_DELYTELSE, 8L, listOf("VEDTAK_ID", "LINJE_ID", "TYPE_DELYTELSE", "FOM", "TOM", "BELOP")),
        Triple(InfotrygdTabeller.T_BELOPSTYPE, 4L, listOf("TYPE", "TEKST", "BEHANDLING")),
        Triple(InfotrygdTabeller.T_DELYTELSESTYPE, 3L, listOf("TYPE", "TEKST", "FRADRAG_TILLEGG")),
        Triple(InfotrygdTabeller.T_KLASSENIVAA, 4L, listOf("KODE", "TEKST")),
        Triple(InfotrygdTabeller.T_ROLLE, 4L, listOf("VEDTAK_ID", "TYPE", "PERSON_LOPENR_R", "FOM", "TOM")),
        Triple(InfotrygdTabeller.T_BESLUT, 5L, listOf("VEDTAK_ID", "BESLUT_ID", "BRUKERID_1", "GODKJ_1", "BRUKERID_2", "GODKJ_2")),
        Triple(InfotrygdTabeller.T_ENDRING, 3L, listOf("VEDTAK_ID", "KODE")),
        Triple(InfotrygdTabeller.T_SU, 5L, listOf("VEDTAK_ID", "VALGT_BEREGN_GRL", "REVURD_DATO")),
        Triple(InfotrygdTabeller.T_STONADSKLASSE, 5L, listOf("VEDTAK_ID", "KLASSIFISERING")),
        Triple(InfotrygdTabeller.T_BEREGN_GRL, 2L, listOf("BEREGN_GRL_ID", "BELOP", "FOM")),
        Triple(InfotrygdTabeller.T_BEREGN_FAKTOR, 2L, listOf("FAKTOR_ID", "VERDI", "FOM")),
        Triple(InfotrygdTabeller.T_KJOREPLAN_AVST, 2L, listOf("DATO_KJORING", "DATO_AVST")),
        Triple(InfotrygdTabeller.T_MAP_DELYTELSE, 8L, listOf("VEDTAK_ID", "LINJE_ID", "OPPDRAG_LINJE_ID")),
    )

    val tabeller = tabellInfo.map { (navn, antall, kolonner) ->
        NyHistoriskTabellimport(tabellnavn = navn, forventetAntall = antall, kolonner = kolonner)
    }
    val import = historiskImportRepo.opprettImport(tabeller)

    tabeller.forEach { tabell ->
        historiskImportRepo.lagreSide(
            HistoriskRådataSide(
                importId = import.id,
                tabellnavn = tabell.tabellnavn,
                side = 0,
                nesteIterator = null,
                rader = (1..tabell.forventetAntall.toInt()).map { radnr ->
                    tabell.kolonner.associateWith { "seed_$radnr" }
                },
            ),
        )
    }
    historiskImportRepo.fullførImport(import.id)

    // Opprett en feilet import slik at frontend kan teste begge statuser.
    // Må fullføres eller feiles før neste opprettImport pga. unik indeks på PÅGÅR.
    val feiletTabeller = listOf(
        NyHistoriskTabellimport(tabellnavn = InfotrygdTabeller.T_STONAD, forventetAntall = 0L, kolonner = listOf("STONAD_ID", "PERSON_LOPENR")),
    )
    val feiletImport = historiskImportRepo.opprettImport(feiletTabeller)
    historiskImportRepo.markerFeilet(feiletImport.id, "Antallsavvik(tabellnavn=T_STONAD, forventet=0, faktisk=1)")
}

sealed interface KunneIkkeImportereHistoriskeData {
    data class Klientfeil(val operasjon: String, val feil: ClientError) : KunneIkkeImportereHistoriskeData
    data class ManglendeTabeller(val tabeller: Set<String>) : KunneIkkeImportereHistoriskeData
    data class UgyldigSidestørrelse(
        val antallRaderPerSide: Long,
        val maksAntallRaderPerSide: Long,
    ) : KunneIkkeImportereHistoriskeData

    data class ImportPågår(val importId: UUID, val opprettet: no.nav.su.se.bakover.common.tid.Tidspunkt) : KunneIkkeImportereHistoriskeData
    data class UgyldigSkjema(val tabellnavn: String, val beskrivelse: String) : KunneIkkeImportereHistoriskeData
    data class IteratorLoop(val tabellnavn: String, val iterator: String) : KunneIkkeImportereHistoriskeData
    data class UgyldigRadbredde(
        val tabellnavn: String,
        val side: Long,
        val radnummer: Int,
        val forventet: Int,
        val faktisk: Int,
    ) : KunneIkkeImportereHistoriskeData

    data class Antallsavvik(
        val tabellnavn: String,
        val forventet: Long,
        val faktisk: Long,
    ) : KunneIkkeImportereHistoriskeData

    data object UventetFeil : KunneIkkeImportereHistoriskeData
}

sealed interface KunneIkkeSletteImport {
    data object IkkeFunnet : KunneIkkeSletteImport
    data object ImportPågår : KunneIkkeSletteImport
}
