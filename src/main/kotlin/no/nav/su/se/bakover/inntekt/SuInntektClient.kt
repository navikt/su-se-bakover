package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.Environment.Companion.SU_INNTEKT_URL

class SuInntektClient {
    fun inntekt(): String {
        val url = "$SU_INNTEKT_URL/inntekt"
        val (_, _, result) = url.httpGet().responseString()
        return result.get()
    }
}