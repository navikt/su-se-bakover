package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpGet

class SuInntektClient(private val baseUrl: String) {
    fun inntekt(): String {
        val url = "$baseUrl/inntekt"
        val (_, _, result) = url.httpGet().responseString()
        return result.get()
    }
}