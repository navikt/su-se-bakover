package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import org.slf4j.MDC

class SuInntektClient(suInntektBaseUrl: String) {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "fnr"

    fun inntekt(ident: String, suInntektToken: String): String {
        val (_, _, result) = inntektResource.httpPost(listOf(suInntektIdentLabel to ident))
                .header(Authorization, "Bearer $suInntektToken")
                .header(XRequestId, MDC.get(XRequestId))
                .responseString()
        return result.get()
    }
}