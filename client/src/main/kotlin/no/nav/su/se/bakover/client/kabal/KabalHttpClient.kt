package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeTilKlageinstans
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

const val OVERSENDELSE_PATH = "/api/oversendelse/v3/sak"

/**
 * For docs og swagger, se [KabalRequest]
 */
class KabalHttpClient(
    private val kabalConfig: ApplicationConfig.ClientsConfig.KabalConfig,
    private val exchange: AzureAd,
) : KlageClient {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build()

    /**
     * TODO jah: klage-teamet mente vi har mulighet til å bytte til OnBehalfOf-token. Men de gjør ingen særskilt tilgangsjekk på brukeren.
     */
    private fun hentToken(): Either<KunneIkkeOversendeTilKlageinstans, String> {
        return Either.catch {
            exchange.getSystemToken(kabalConfig.clientId)
        }.mapLeft { throwable ->
            log.error(
                "Kunne ikke lage token for Kabal med klientId: ${kabalConfig.clientId}",
                throwable,
            )
            KunneIkkeOversendeTilKlageinstans
        }
    }

    override fun sendTilKlageinstans(
        klage: OversendtKlage,
        journalpostIdForVedtak: JournalpostId,
    ): Either<KunneIkkeOversendeTilKlageinstans, Unit> {
        val token = hentToken().getOrElse { return it.left() }
        val requestBody = serialize(
            KabalRequestMapper.map(
                klage = klage,
                journalpostIdForVedtak = journalpostIdForVedtak,
            ),
        )
        return Either.catch {
            val request = HttpRequest.newBuilder().uri(URI.create("${kabalConfig.url}$OVERSENDELSE_PATH"))
                .header("Authorization", "Bearer $token").header("Accept", "application/json")
                .header("Nav-Callid", getOrCreateCorrelationIdFromThreadLocal().toString())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let { res ->
                val responseBody = res.body()
                if (res.isSuccess()) {
                    log.info("Klage sendt til Kabal/KA, med klageId=${klage.id}, responseStatus=${res.statusCode()}")
                    sikkerLogg.info("Klage sendt med klageId=${klage.id}, request=$requestBody, response=$responseBody, responseStatus=${res.statusCode()}")
                    Unit.right()
                } else {
                    log.error("Feil ved oversendelse til Kabal/KA, status=${res.statusCode()}, body=$responseBody. Se sikkerlogg for request.")
                    sikkerLogg.error("Feil ved oversendelse til Kabal/KA, request: $requestBody")
                    return KunneIkkeOversendeTilKlageinstans.left()
                }
            }
        }.mapLeft { throwable ->
            if (throwable is IOException) {
                log.error("Feil ved oversendelse til Kabal/KA: Nettverksfeil. Er Kabal oppe?", throwable)
                sikkerLogg.error(
                    "Feil ved oversendelse til Kabal/KA: Nettverksfeil. Er Kabal oppe? requestBody=$requestBody",
                    throwable,
                )
            } else {
                log.error("Feil ved oversendelse til Kabal/KA: Ukjent feil", throwable)
                sikkerLogg.error("Feil ved oversendelse til Kabal/KA: Ukjent feil. requestBody=$requestBody", throwable)
            }
            KunneIkkeOversendeTilKlageinstans
        }.flatten()
    }
}
