package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import org.slf4j.MDC

class SuInntektClient(suInntektBaseUrl: String) {
    private val inntektResource = "$suInntektBaseUrl/inntekt"
    private val suInntektIdentLabel = "ident"

    fun inntekt(ident: String, suInntektToken: String): String {
        val (_, _, result) = inntektResource.httpGet(listOf(suInntektIdentLabel to ident))
                .header(Authorization, "Bearer $suInntektToken")
                .header(XRequestId, MDC.get(XRequestId))
                .responseString()
        return result.get()
    }
}