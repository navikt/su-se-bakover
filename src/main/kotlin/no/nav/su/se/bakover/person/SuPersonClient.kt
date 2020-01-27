package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import org.slf4j.MDC

class SuPersonClient(suPersonBaseUrl: String) {
    private val personResource = "$suPersonBaseUrl/person"
    private val suPersonIdentLabel = "ident"

    internal fun person(ident: String, suPersonToken: String): SuPersonResponse {
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident))
                .header(Authorization, "Bearer $suPersonToken")
                .header(XRequestId, MDC.get(XRequestId))
                .responseString()
        return result.fold(
                { SuPersonOk(it) },
                { SuPersonFeil(it.response.statusCode, it.message ?: it.toString()) }
        )
    }
}

internal sealed class SuPersonResponse
internal class SuPersonOk(val json: String) : SuPersonResponse()
internal class SuPersonFeil(val httpCode: Int, val message: String) : SuPersonResponse()