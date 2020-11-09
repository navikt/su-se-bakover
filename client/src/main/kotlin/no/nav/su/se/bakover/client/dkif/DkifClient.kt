package no.nav.su.se.bakover.client.dkif

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.domain.Fnr
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal const val dkifPath = "/api/v1/personer/kontaktinformasjon"

class DkifClient(val baseUrl: String, val tokenOppslag: TokenOppslag) : Dkif {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentKontaktinformasjon(fnr: Fnr): Either<ClientError, Kontaktinformasjon> {
        val (_, response, result) = "$baseUrl$dkifPath".httpGet()
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .header("Nav-Personidenter", listOf(fnr.toString())).responseString()

        return result.fold(
            { json ->
                JSONObject(json)?.let {
                    Kontaktinformasjon(
                        epostadresse = it.optString("epostadresse"),
                        mobiltelefonnummer = it.optString("mobiltelefonnummer"),
                        reservert = it.optBoolean("reservert"),
                        kanVarsles = it.optBoolean("kanVarsles"),
                        spraak = it.optString("spraak")
                    ).right()
                }
            },
            {
                val errorMessage = "Feil ved henting av digital kontaktinformasjon"
                log.error(errorMessage)
                ClientError(response.statusCode, errorMessage).left()
            }
        )
    }
}
