package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import org.slf4j.MDC

const val suPersonIdentLabel = "ident"

class SuPersonClient(private val baseUrl: String) {
    fun person(ident: String, suPersonToken: String): String {
        val (_, _, result) = baseUrl.httpGet(listOf(suPersonIdentLabel to ident))
                .header(Authorization, "Bearer $suPersonToken")
                .header(XRequestId, MDC.get(XRequestId))
                .responseString()
        return result.get()
    }
}