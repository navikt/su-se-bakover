package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpPost
import io.ktor.application.Application
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class SuInntektClient(suInntektBaseUrl: String) {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    fun inntekt(ident: String, suInntektToken: String): String {
        val (_, _, result) = inntektResource.httpPost(
            listOf(
                suInntektIdentLabel to ident,
                "fom" to "2020-01",
                "tom" to "2020-12"
            )
        )
            .header(Authorization, "Bearer $suInntektToken")
            .header(XRequestId, MDC.get(XRequestId))
            .header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
            .responseString()

        if (result.component2() != null) {
            LoggerFactory.getLogger(SuInntektClient::class.java)
                .warn("Kunne ikke hente inntekter. ${result.component2()!!.response.statusCode} : ${result.component2()!!.response.responseMessage}")
        }
        return result.get()
    }
}