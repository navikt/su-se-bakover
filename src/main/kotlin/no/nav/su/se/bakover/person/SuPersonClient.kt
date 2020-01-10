package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders

const val suPersonIdentLabel = "ident"

class SuPersonClient(private val baseUrl: String) {
    fun person(ident: String, suPersonToken: String): String {
        val (_, _, result) = baseUrl.httpGet(listOf(suPersonIdentLabel to ident))
            .header(HttpHeaders.Authorization,"Bearer $suPersonToken")
            .responseString()
        return result.get()
    }
}