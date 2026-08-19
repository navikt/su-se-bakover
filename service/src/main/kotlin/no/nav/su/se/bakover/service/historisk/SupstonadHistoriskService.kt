package no.nav.su.se.bakover.service.historisk

import arrow.core.Either
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.client.historisk.SupstonadHistoriskClient
import no.nav.su.se.bakover.client.historisk.UttrekkResponse
import no.nav.su.se.bakover.common.domain.client.ClientError
import org.slf4j.LoggerFactory

/**
 * Enkel tjeneste som eksponerer [SupstonadHistoriskClient] mot web-laget.
 *
 * Den er et tynt lag over klienten, men logger hvert steg i flyten slik at vi kan følge
 * kallene mot supstonad-historisk i loggene (se også [SupstonadHistoriskClient] som
 * logger HTTP- og deserialiseringsfeil).
 */
class SupstonadHistoriskService(
    private val supstonadHistoriskClient: SupstonadHistoriskClient,
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
}
