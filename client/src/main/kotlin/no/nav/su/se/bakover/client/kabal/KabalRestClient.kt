package no.nav.su.se.bakover.client.kabal

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import org.slf4j.LoggerFactory

const val oversendelsePath = "/api/oversendelse/v1/klage"

class KabalRestClient(val baseUrl: String) : KabalClient {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun sendTilKlageinstans(klage: IverksattKlage): Either<OversendelseFeilet, Unit> {
        val (_, res, result) = "$baseUrl$oversendelsePath".httpPost()
            .authentication()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", getOrCreateCorrelationId())
            .responseString()

        return result.fold(
            { _ ->
                log.info("Sender klage til Kabal")
                Unit.right()
            },
            {
                log.error("Feil ved oversendelse til Kabal/KA, status=${res.statusCode} body=${String(res.data)}", it)
                return OversendelseFeilet.left()
            }
        )
    }
}
