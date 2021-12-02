package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import org.slf4j.LoggerFactory
import org.slf4j.MDC

const val oversendelsePath = "/api/oversendelse/v1/klage"

class KabalRestClient(
    private val kabalConfig: ApplicationConfig.ClientsConfig.KabalConfig,
    private val exchange: OAuth,
) : KabalClient {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun onBehalfOfToken(): Either<KabalFeil.KunneIkkeLageToken, String> {
        return Either.catch {
            exchange.onBehalfOfToken(MDC.get("Authorization"), kabalConfig.clientId)
        }.mapLeft { throwable ->
            log.error(
                "Kunne ikke lage onBehalfOfToken for oppgave med klient id ${kabalConfig.clientId}",
                throwable,
            )
            KabalFeil.KunneIkkeLageToken
        }
    }

    override fun sendTilKlageinstans(klage: IverksattKlage, sak: Sak): Either<KabalFeil, Unit> {
        val token = onBehalfOfToken().getOrHandle { return it.left() }

        val (_, res, result) = "${kabalConfig.url}$oversendelsePath".httpPost()
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", getOrCreateCorrelationId())
            .body(objectMapper.writeValueAsString(KabalRequestMapper.map(klage, sak)))
            .responseString()

        return result.fold(
            { _ ->
                log.info("Sender klage til Kabal")
                Unit.right()
            },
            {
                log.error("Feil ved oversendelse til Kabal/KA, status=${res.statusCode} body=${String(res.data)}", it)
                return KabalFeil.OversendelseFeilet.left()
            },
        )
    }
}
