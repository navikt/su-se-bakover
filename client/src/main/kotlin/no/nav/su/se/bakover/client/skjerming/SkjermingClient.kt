package no.nav.su.se.bakover.client.skjerming

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.domain.Fnr
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface Skjerming {
    fun erSkjermet(ident: Fnr): Boolean
}
internal class SkjermingClient(private val skjermingUrl: String) : Skjerming {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    override fun erSkjermet(ident: Fnr): Boolean {
        val (_, response, result) = "$skjermingUrl/skjermet?personident=$ident".httpGet().responseString()

        return result.fold(
            {
                it === "true"
            },
            {
                log.warn(
                    "Feil i kallet mot skjermingsregisteret.",
                    response.statusCode,
                    response.body().asString("application/json"),
                    it
                )
                throw RuntimeException("Feil i kallet mot skjermingsregisteret.")
            }
        )
    }
}
