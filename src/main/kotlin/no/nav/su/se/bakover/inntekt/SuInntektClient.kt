package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.Environment

class SuInntektClient(private val environment: Environment) {
    fun inntekt(): String {
        val url = "${environment.suInntektUrl}/inntekt"
        val (_, _, result) = url.httpGet().responseString()
        return result.get()
    }
}