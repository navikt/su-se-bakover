package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XRequestId
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class SuInntektClient(suInntektBaseUrl: String) {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    fun inntekt(ident: String, suInntektToken: String, fomDato: String, tomDato: String): String {
        val (_, _, result) = inntektResource.httpPost(
                listOf(
                        suInntektIdentLabel to ident,
                        "fom" to fomDato.substring(0, 7),
                        "tom" to tomDato.substring(0, 7)
                )
        )
                .header(Authorization, "Bearer $suInntektToken")
                .header(XRequestId, MDC.get(XRequestId))
                .header(ContentType, FormUrlEncoded)
                .responseString()

        if (result.component2() != null) {
            LoggerFactory.getLogger(SuInntektClient::class.java)
                    .warn("Kunne ikke hente inntekter. ${result.component2()!!.response.statusCode} : ${result.component2()!!.response.responseMessage}")
        }
        return result.get()
    }
}