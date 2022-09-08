package no.nav.su.se.bakover.client.krr

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import org.slf4j.LoggerFactory

internal const val personPath = "/rest/v1/person"

/**
 * @see https://digdir-krr-proxy.dev.intern.nav.no/swagger-ui/index.html#/
 */
class KontaktOgReservasjonsregisterClient(
    val config: ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig,
    val azure: AzureAd,
) : KontaktOgReservasjonsregister {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentKontaktinformasjon(fnr: Fnr): Either<KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon> {
        val (_, response, result) = "${config.url}$personPath".httpGet()
            .authentication().bearer(azure.getSystemToken(config.appId))
            .header("Accept", "application/json")
            .header("Nav-Call-Id", getOrCreateCorrelationId())
            .header("Nav-Personident", fnr.toString())
            .responseString()

        return result.fold(
            { json ->
                return objectMapper.readValue<HentKontaktinformasjonRepsonse>(json).toKontaktinformasjon().right()
            },
            {
                val errorMessage = "Feil ved henting av digital kontaktinformasjon. Status=${response.statusCode} Body=${String(response.data)}"
                if (response.statusCode == 500) {
                    // Eksempel json-response: `{"melding":"Det oppsto en feil ved kall til Maskinporten"}`
                    log.warn(errorMessage, it)
                } else {
                    log.error(errorMessage, it)
                }
                KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.left()
            }
        )
    }
}

private data class HentKontaktinformasjonRepsonse(
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
