package no.nav.su.se.bakover.client.skjerming

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.common.CorrelationId.Companion.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.CorrelationIdHeader
import no.nav.su.se.bakover.common.Fnr
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface Skjerming {
    fun erSkjermet(ident: Fnr): Boolean
}

internal class SkjermingClient(private val skjermingUrl: String) : Skjerming {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    override fun erSkjermet(ident: Fnr): Boolean {
        val (_, response, result) = "$skjermingUrl/skjermet?personident=$ident".httpGet()
            .header("Accept", "application/json")
            .header(CorrelationIdHeader, getOrCreateCorrelationIdFromThreadLocal())
            .responseString()

        return result.fold(
            {
                log.info("Hentet skjermingsstatus fra skjermingsregisteret")
                it == "true"
            },
            {
                val body = response.body().asString("application/json")
                log.error(
                    "Feil i kallet mot skjermingsregisteret. Status Code: ${response.statusCode}, body: $body",
                    it,
                )
                throw RuntimeException("Feil i kallet mot skjermingsregisteret.")
            },
        )
    }
}
