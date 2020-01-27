package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import no.nav.su.se.bakover.Result
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class SuPersonClient(suPersonBaseUrl: String) {
    private val personResource = "$suPersonBaseUrl/person"
    private val suPersonIdentLabel = "ident"

    internal fun person(ident: String, suPersonToken: String): Result {
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident))
                .header(Authorization, "Bearer $suPersonToken")
                .header(XRequestId, MDC.get(XRequestId))
                .responseString()
        return result.fold(
                { Ok(it) },
                {
                    val errorMessage = it.response.body().asString(ContentType.Application.Json.toString())
                    val statusCode = it.response.statusCode
                    logger.debug("Kall mot PDL feilet, statuskode: $statusCode, feilmelding: $errorMessage");
                    Feil(statusCode, errorMessage)
                }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuPersonClient::class.java)
    }
}

