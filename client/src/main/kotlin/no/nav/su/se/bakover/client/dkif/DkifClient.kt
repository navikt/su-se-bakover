package no.nav.su.se.bakover.client.dkif

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import org.slf4j.LoggerFactory

internal const val dkifPath = "/api/v1/personer/kontaktinformasjon"

// Dokumentasjon: https://dkif.dev.adeo.no/swagger-ui.html
class DkifClient(
    val baseUrl: String,
    val tokenOppslag: TokenOppslag,
    private val consumerId: String,
) : DigitalKontaktinformasjon {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentKontaktinformasjon(fnr: Fnr): Either<DigitalKontaktinformasjon.KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon> {
        val (_, response, result) = "$baseUrl$dkifPath".httpGet()
            .authentication().bearer(tokenOppslag.token().value)
            .header("Accept", "application/json")
            .header("Nav-Consumer-Id", consumerId)
            .header("Nav-Call-Id", getOrCreateCorrelationId())
            .header("Nav-Personidenter", listOf(fnr.toString())).responseString()

        return result.fold(
            { json ->
                val resultat: DkifResultat = objectMapper.readValue(json)
                return resultat.kontaktinfo?.get(fnr.toString())?.toKontaktinformasjon()?.right()
                    ?: DigitalKontaktinformasjon.KunneIkkeHenteKontaktinformasjon.left().also {
                        log.warn("Feil i kontaktinfo: ${resultat.feil?.get(fnr.toString())?.melding ?: "Ukjent"}")
                    }
            },
            {
                val errorMessage = "Feil ved henting av digital kontaktinformasjon. Status=${response.statusCode} Body=${String(response.data)}"
                if (response.statusCode == 500) {
                    // Eksempel json-response: `{"melding":"Det oppsto en feil ved kall til Maskinporten"}`
                    log.warn(errorMessage, it)
                } else {
                    log.error(errorMessage, it)
                }
                DigitalKontaktinformasjon.KunneIkkeHenteKontaktinformasjon.left()
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

private data class DkifKontaktinfo(
    val reservert: Boolean,
    val kanVarsles: Boolean,
    val epostadresse: String?,
    val mobiltelefonnummer: String?,
    val spraak: String?
) {
    fun toKontaktinformasjon(): Kontaktinformasjon {
        return Kontaktinformasjon(
            epostadresse = epostadresse,
            mobiltelefonnummer = mobiltelefonnummer,
            reservert = reservert,
            kanVarsles = kanVarsles,
            språk = spraak
        )
    }
}
