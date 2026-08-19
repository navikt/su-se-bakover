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
        val kildeSkjema = validerOgHentKildeSkjema().getOrElse { return it.left() }
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

    private fun validerOgHentKildeSkjema(): Either<KunneIkkeImportereHistoriskeData, Map<String, List<String>>> {
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

            validerSide(tabell, uttrekk, bruktIteratorer)?.let { return it }

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
        return null
    }

    private fun validerSide(
        tabell: HistoriskImport.Tabell,
        uttrekk: UttrekkResponse,
        bruktIteratorer: MutableSet<String>,
    ): KunneIkkeImportereHistoriskeData? {
        if (uttrekk.schema.kolonner.map { it.navn } != tabell.kolonner) {
            return KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                tabell.tabellnavn,
                "Skjema i uttrekk er ikke lik skjemaet som importen ble startet med",
            )
        }
        if (uttrekk.iterator.isNotBlank() && !bruktIteratorer.add(uttrekk.iterator)) {
            return KunneIkkeImportereHistoriskeData.IteratorLoop(tabell.tabellnavn, uttrekk.iterator)
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
        val totaltImportert = tabell.importertAntall + uttrekk.innhold.size
        val erSisteSide = uttrekk.iterator.isBlank()
        if (totaltImportert > tabell.forventetAntall || (erSisteSide && totaltImportert != tabell.forventetAntall)) {
            return KunneIkkeImportereHistoriskeData.Antallsavvik(
                tabellnavn = tabell.tabellnavn,
                forventet = tabell.forventetAntall,
                faktisk = totaltImportert,
            )
        }
        return null
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
    }
}

data class HistoriskImportresultat(
    val importId: UUID,
    val importerteRader: Long,
    val importerteTabeller: Int,
)

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
