package no.nav.su.se.bakover.client.dkif

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal const val dkifPath = "/api/v1/personer/kontaktinformasjon"

class DkifClient(val baseUrl: String, val tokenOppslag: TokenOppslag, val consumerId: String) : Dkif {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentKontaktinformasjon(fnr: Fnr): Either<ClientError, Kontaktinformasjon> {
        val (_, response, result) = "$baseUrl$dkifPath".httpGet()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Nav-Consumer-Id", consumerId)
            .header("Nav-Call-Id", MDC.get("X-Correlation-ID"))
            .header("Nav-Personidenter", listOf(fnr.toString())).responseString()

        return result.fold(
            { json ->
                val resultat: DkifResultat = objectMapper.readValue(json)
                val kontaktinfo: DkifKontaktinfo? = resultat.kontaktinfo?.get(fnr.fnr)

                if (kontaktinfo != null) {
                    Kontaktinformasjon(
                        epostadresse = kontaktinfo.epostadresse,
                        mobiltelefonnummer = kontaktinfo.mobiltelefonnummer,
                        reservert = kontaktinfo.reservert,
                        kanVarsles = kontaktinfo.kanVarsles,
                        språk = kontaktinfo.spraak
                    ).right()
                } else {
                    val errorMessage = "Feil i kontaktinfo: ${resultat.feil?.get(fnr.fnr)?.melding ?: "Ukjent"}"
                    log.error(errorMessage)
                    return ClientError(500, errorMessage).left()
                }
            },
            {
                val errorMessage = "Feil ved henting av digital kontaktinformasjon"
                log.error("$errorMessage. Status=${response.statusCode} Body=${String(response.data)}", it)
                ClientError(response.statusCode, errorMessage).left()
            }
        )
    }
}

private data class DkifResultat(
    val feil: Map<String, DkifFeil>?,
    val kontaktinfo: Map<String, DkifKontaktinfo>?
)

private data class DkifFeil(
    val melding: String
)

class DkifKontaktinfo(
    val reservert: Boolean,
    val kanVarsles: Boolean,
    val epostadresse: String?,
    val mobiltelefonnummer: String?,
    val spraak: String?
)
