package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet

class SuPersonClient(private val baseUrl: String) {
    fun person(): String {
        val url = "$baseUrl/isalive"
        val (_, _, result) = url.httpGet().responseString()
        return result.get()
    }
}