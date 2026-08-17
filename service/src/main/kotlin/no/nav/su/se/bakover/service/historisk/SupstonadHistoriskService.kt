package no.nav.su.se.bakover.service.historisk

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.client.historisk.SupstonadHistoriskClient
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import org.slf4j.LoggerFactory

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

    /**
     * Kopierer alle avtalte tabeller til et rådatasnapshot.
     *
     * Ved klientfeil beholdes importen som PÅGÅR og neste kall fortsetter fra siste committede side. Ved skjema- eller
     * antallsavvik merkes importen som FEILET fordi en blanding av to ulike kildesnapshots ikke kan brukes sikkert.
     * Metoden er ment kalt fra en langvarig jobb, ikke direkte i en HTTP-request.
     */
    fun importerAlleTabeller(
        antallRaderPerSide: Long = STANDARD_ANTALL_RADER_PER_SIDE,
    ): Either<KunneIkkeImportereHistoriskeData, HistoriskImportresultat> {
        if (antallRaderPerSide !in 1..MAKS_ANTALL_RADER_PER_SIDE) {
            return KunneIkkeImportereHistoriskeData.UgyldigSidestørrelse(
                antallRaderPerSide = antallRaderPerSide,
                maksAntallRaderPerSide = MAKS_ANTALL_RADER_PER_SIDE,
            ).left()
        }

        val tilgjengeligeTabeller = supstonadHistoriskClient.hentTabeller().getOrElse {
            return KunneIkkeImportereHistoriskeData.Klientfeil("hentTabeller", it).left()
        }
        val manglendeTabeller = TABELLER_SOM_SKAL_IMPORTERES - tilgjengeligeTabeller.keys
        if (manglendeTabeller.isNotEmpty()) {
            return KunneIkkeImportereHistoriskeData.ManglendeTabeller(manglendeTabeller).left()
        }

        val ugyldigSkjema = TABELLER_SOM_SKAL_IMPORTERES.firstNotNullOfOrNull { tabellnavn ->
            val kolonner = tilgjengeligeTabeller.getValue(tabellnavn)
            when {
                kolonner.isEmpty() -> KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                    tabellnavn,
                    "Tabellen mangler kolonner",
                )

                kolonner.any { it.isBlank() } -> KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                    tabellnavn,
                    "Tabellen har blankt kolonnenavn",
                )

                kolonner.distinct().size != kolonner.size -> KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                    tabellnavn,
                    "Tabellen har duplikate kolonnenavn",
                )

                else -> null
            }
        }
        if (ugyldigSkjema != null) return ugyldigSkjema.left()

        val import = historiskImportRepo.hentPågåendeImport()
            ?: opprettImport(tilgjengeligeTabeller).getOrElse { return it.left() }

        val forventedeTabeller = TABELLER_SOM_SKAL_IMPORTERES.sorted()
        if (import.tabeller.map { it.tabellnavn } != forventedeTabeller) {
            return markerFeilet(
                import,
                KunneIkkeImportereHistoriskeData.UgyldigPågåendeImport(
                    "Tabellene i pågående import er ikke lik avtalt tabellsett",
                ),
            )
        }

        import.tabeller.forEach { lagretTabell ->
            val kildekolonner = tilgjengeligeTabeller.getValue(lagretTabell.tabellnavn)
            if (lagretTabell.kolonner != kildekolonner) {
                return markerFeilet(
                    import,
                    KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                        lagretTabell.tabellnavn,
                        "Kildeskjemaet er endret etter at importen startet",
                    ),
                )
            }

            var tabell = lagretTabell
            while (tabell.status == HistoriskImport.Status.PÅGÅR) {
                val uttrekk = supstonadHistoriskClient.hentUttrekk(
                    tabellnavn = tabell.tabellnavn,
                    antallRader = antallRaderPerSide,
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

                val responskolonner = uttrekk.schema.kolonner.map { it.navn }
                if (responskolonner != tabell.kolonner) {
                    return markerFeilet(
                        import,
                        KunneIkkeImportereHistoriskeData.UgyldigSkjema(
                            tabell.tabellnavn,
                            "Skjema i uttrekk er ikke lik skjemaet som importen ble startet med",
                        ),
                    )
                }
                if (uttrekk.iterator.isNotBlank() && uttrekk.iterator == tabell.nesteIterator) {
                    return markerFeilet(
                        import,
                        KunneIkkeImportereHistoriskeData.IteratorStårStille(tabell.tabellnavn),
                    )
                }

                val rader = uttrekk.innhold.mapIndexed { radnummer, verdier ->
                    if (verdier.size != tabell.kolonner.size) {
                        return markerFeilet(
                            import,
                            KunneIkkeImportereHistoriskeData.UgyldigRadbredde(
                                tabellnavn = tabell.tabellnavn,
                                side = tabell.nesteSide,
                                radnummer = radnummer,
                                forventet = tabell.kolonner.size,
                                faktisk = verdier.size,
                            ),
                        )
                    }
                    tabell.kolonner.zip(verdier).toMap()
                }

                val nyttAntall = tabell.importertAntall + rader.size
                if (nyttAntall > tabell.forventetAntall ||
                    (uttrekk.iterator.isBlank() && nyttAntall != tabell.forventetAntall)
                ) {
                    return markerFeilet(
                        import,
                        KunneIkkeImportereHistoriskeData.Antallsavvik(
                            tabellnavn = tabell.tabellnavn,
                            forventet = tabell.forventetAntall,
                            faktisk = nyttAntall,
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
        }

        historiskImportRepo.fullførImport(import.id)
        return HistoriskImportresultat(
            importId = import.id,
            importerteRader = import.tabeller.sumOf { it.forventetAntall },
            importerteTabeller = import.tabeller.size,
        ).right()
    }

    private fun opprettImport(
        tilgjengeligeTabeller: Map<String, List<String>>,
    ): Either<KunneIkkeImportereHistoriskeData, HistoriskImport> {
        val tabeller = TABELLER_SOM_SKAL_IMPORTERES.sorted().map { tabellnavn ->
            val antall = supstonadHistoriskClient.tellRader(tabellnavn).getOrElse {
                return KunneIkkeImportereHistoriskeData.Klientfeil("tellRader", it).left()
            }
            NyHistoriskTabellimport(
                tabellnavn = tabellnavn,
                forventetAntall = antall.antall,
                kolonner = tilgjengeligeTabeller.getValue(tabellnavn),
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
            "INFOTRYGD_SUQ.T_BELOPSTYPE",
            "INFOTRYGD_SUQ.T_BEREGN_FAKTOR",
            "INFOTRYGD_SUQ.T_BEREGN_GRL",
            "INFOTRYGD_SUQ.T_BESLUT",
            "INFOTRYGD_SUQ.T_DELYTELSE",
            "INFOTRYGD_SUQ.T_DELYTELSESTYPE",
            "INFOTRYGD_SUQ.T_ENDRING",
            "INFOTRYGD_SUQ.T_KJOREPLAN_AVST",
            "INFOTRYGD_SUQ.T_KLASSENIVAA",
            "INFOTRYGD_SUQ.T_LOPENR_FNR",
            "INFOTRYGD_SUQ.T_MAP_DELYTELSE",
            "INFOTRYGD_SUQ.T_ROLLE",
            "INFOTRYGD_SUQ.T_STONAD",
            "INFOTRYGD_SUQ.T_STONADSKLASSE",
            "INFOTRYGD_SUQ.T_SU",
            "INFOTRYGD_SUQ.T_VEDTAK",
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
    data class IteratorStårStille(val tabellnavn: String) : KunneIkkeImportereHistoriskeData
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
