package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders

const val suInntektIdentLabel = "ident"

class SuInntektClient(private val baseUrl: String) {
    fun inntekt(ident: String, suInntektToken: String): String {
        val (_, _, result) = baseUrl.httpGet(listOf(suInntektIdentLabel to ident))
            .header(HttpHeaders.Authorization,"Bearer $suInntektToken")
            .responseString()
        return result.get()
    }
}