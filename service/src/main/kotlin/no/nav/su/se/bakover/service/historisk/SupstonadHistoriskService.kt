package no.nav.su.se.bakover.service.historisk

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.client.historisk.SupstonadHistoriskClient
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskImportOversikt
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
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

    fun slettImport(importId: UUID) {
        log.info("Historisk import: sletter import {}", importId)
        historiskImportRepo.slettImport(importId)
        log.info("Historisk import: import {} slettet", importId)
    }

    /**
     *
     * Ved klientfeil beholdes importen som PÅGÅR og neste kall fortsetter fra siste committede side. Ved skjema- eller
     * antallsavvik merkes importen som FEILET fordi en blanding av to ulike kildesnapshots ikke kan brukes sikkert.
     * Metoden er ment kalt fra en langvarig jobb, ikke direkte i en HTTP-request.
     *
     * TODO: en route fra driftssiden skal trigge denne må også ha en get som viser siste import med daata
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

        log.info("Historisk import: henter og validerer kildeskjema")
        val kildeSkjema = validerOgHentKildeSkjema().getOrElse { return it.left() }
        log.info("Historisk import: kildeskjema OK, {} tabeller", kildeSkjema.size)

        val eksisterende = historiskImportRepo.hentPågåendeImport()
        val import = if (eksisterende != null) {
            log.info("Historisk import: gjenopptar pågående import {}", eksisterende.id)
            eksisterende
        } else {
            log.info("Historisk import: oppretter ny import")
            opprettImport(kildeSkjema).getOrElse { return it.left() }.also {
                log.info("Historisk import: opprettet import {} med {} tabeller", it.id, it.tabeller.size)
            }
        }

        validerPågåendeImportMotKilde(import, kildeSkjema)
            ?.let { return markerFeilet(import, it) }

        import.tabeller.forEach { lagretTabell ->
            importerTabell(import, lagretTabell, sideStørrelse)
                ?.let { return it }
        }

        historiskImportRepo.fullførImport(import.id)
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

    private fun validerPågåendeImportMotKilde(
        import: HistoriskImport,
        kildeSkjema: Map<String, List<String>>,
    ): KunneIkkeImportereHistoriskeData? {
        if (import.tabeller.map { it.tabellnavn } != TABELLER_SOM_SKAL_IMPORTERES.sorted()) {
            return KunneIkkeImportereHistoriskeData.UgyldigPågåendeImport(
                "Tabellene i pågående import er ikke lik avtalt tabellsett",
            )
        }
        import.tabeller.forEach { lagretTabell ->
            if (lagretTabell.kolonner != kildeSkjema.getValue(lagretTabell.tabellnavn)) {
                return KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                    lagretTabell.tabellnavn,
                    "Kildeskjemaet er endret etter at importen startet",
                )
            }
        }
        return null
    }

    private fun importerTabell(
        import: HistoriskImport,
        startTabell: HistoriskImport.Tabell,
        sideStørrelse: Long,
    ): Either<KunneIkkeImportereHistoriskeData, Nothing>? {
        var tabell = startTabell
        val seneIteratorer = mutableSetOf<String>()
        tabell.nesteIterator?.let { seneIteratorer.add(it) }

        while (tabell.status == HistoriskImport.Status.PÅGÅR) {
            val uttrekk = supstonadHistoriskClient.hentUttrekk(
                tabellnavn = tabell.tabellnavn,
                antallRader = sideStørrelse,
                iterator = tabell.nesteIterator,
            ).getOrElse {
                log.error(
                    "Historisk import {} stoppet midlertidig ved tabell '{}' side {}. " +
                        "Neste kjøring fortsetter fra lagret checkpoint.",
                    import.id,
                    tabell.tabellnavn,
                    tabell.nesteSide,
                )
                return KunneIkkeImportereHistoriskeData.Klientfeil("hentUttrekk", it).left()
            }

            if (uttrekk.schema.kolonner.map { it.navn } != tabell.kolonner) {
                return markerFeilet(
                    import,
                    KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                        tabell.tabellnavn,
                        "Skjema i uttrekk er ikke lik skjemaet som importen ble startet med",
                    ),
                )
            }
            if (uttrekk.iterator.isNotBlank() && !seneIteratorer.add(uttrekk.iterator)) {
                return markerFeilet(
                    import,
                    KunneIkkeImportereHistoriskeData.IteratorSyklus(tabell.tabellnavn, uttrekk.iterator),
                )
            }

            val rader = uttrekk.innhold.mapIndexed { radnummer, kolonneverdier ->
                if (kolonneverdier.size != tabell.kolonner.size) {
                    return markerFeilet(
                        import,
                        KunneIkkeImportereHistoriskeData.UgyldigRadbredde(
                            tabellnavn = tabell.tabellnavn,
                            side = tabell.nesteSide,
                            radnummer = radnummer,
                            forventet = tabell.kolonner.size,
                            faktisk = kolonneverdier.size,
                        ),
                    )
                }
                tabell.kolonner.zip(kolonneverdier).toMap()
            }

            val totaltImportert = tabell.importertAntall + rader.size
            if (totaltImportert > tabell.forventetAntall ||
                (uttrekk.iterator.isBlank() && totaltImportert != tabell.forventetAntall)
            ) {
                return markerFeilet(
                    import,
                    KunneIkkeImportereHistoriskeData.Antallsavvik(
                        tabellnavn = tabell.tabellnavn,
                        forventet = tabell.forventetAntall,
                        faktisk = totaltImportert,
                    ),
                )
            }

            tabell = historiskImportRepo.lagreSide(
                HistoriskRådataSide(
                    importId = import.id,
                    tabellnavn = tabell.tabellnavn,
                    side = tabell.nesteSide,
                    nesteIterator = uttrekk.iterator.takeUnless { it.isBlank() },
                    rader = rader,
                ),
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
        log.error("Historisk import {} ble markert som feilet: {}", import.id, feil)
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
    val importId: java.util.UUID,
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

    data class UgyldigSkjema(val tabellnavn: String, val beskrivelse: String) : KunneIkkeImportereHistoriskeData
    data class UgyldigPågåendeImport(val beskrivelse: String) : KunneIkkeImportereHistoriskeData
    data class IteratorSyklus(val tabellnavn: String, val iterator: String) : KunneIkkeImportereHistoriskeData
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
}
