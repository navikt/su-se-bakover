package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders

class SuPersonClient(private val baseUrl: String) {
    fun person(suPersonToken: String): String {
        val url = "$baseUrl/isalive"
        val (_, _, result) = url.httpGet()
            .header(HttpHeaders.Authorization,"Bearer $suPersonToken")
            .responseString()
        return result.get()
    }
}