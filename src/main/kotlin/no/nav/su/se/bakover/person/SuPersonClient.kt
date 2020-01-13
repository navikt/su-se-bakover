package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import org.slf4j.MDC

class SuPersonClient(suPersonBaseUrl: String) {
    private val personResource = "$suPersonBaseUrl/person"
    private val suPersonIdentLabel = "ident"

    fun person(ident: String, suPersonToken: String): String {
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident))
                .header(Authorization, "Bearer $suPersonToken")
                .header(XRequestId, MDC.get(XRequestId))
                .responseString()
        return result.get()
    }
}