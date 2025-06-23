package no.nav.su.se.bakover.client.krr

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory

internal const val PERSON_PATH = "/rest/v1/person"
internal const val PERSONER_PATH = "/rest/v1/personer"

/**
 * @see https://digdir-krr-proxy.intern.dev.nav.no/swagger-ui/index.html#/
 */
class KontaktOgReservasjonsregisterClient(
    val config: ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig,
    val azure: AzureAd,
) : KontaktOgReservasjonsregister {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentKontaktinformasjon(fnr: Fnr): Either<KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon> {
        val (_, response, result) = "${config.url}$PERSON_PATH".httpGet()
            .authentication().bearer(azure.getSystemToken(config.appId))
            .header("Accept", "application/json")
            .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
            .header("Nav-Personident", fnr.toString())
            .responseString()

        return result.fold(
            { json ->
                deserialize<HentKontaktinformasjonRepsonse>(json).toKontaktinformasjon()
            },
            {
                val errorMessage =
                    "Feil ved henting av digital kontaktinformasjon. Status=${response.statusCode} Body=${
                        String(response.data)
                    }"
                if (response.statusCode == 500) {
                    // Eksempel json-response: `{"melding":"Det oppsto en feil ved kall til Maskinporten"}`
                    log.warn(errorMessage + "Se sikkerlogg for mer kontekst.", it)
                    sikkerLogg.warn(errorMessage + "Fnr: $fnr", it)
                } else {
                    log.error(errorMessage + "Se sikkerlogg for mer kontekst.", it)
                    sikkerLogg.error(errorMessage + "Fnr: $fnr", it)
                }
                KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
            },
        )
    }

    override fun hentKontaktinformasjonNy(fnr: Fnr): Either<KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon> {
        val request = serialize(
            HentKontaktinformasjonRequest(
                personidenter = listOf(fnr.toString()),
            ),
        )
        val (_, response, result) = "${config.url}$PERSONER_PATH".httpPost()
            .authentication().bearer(azure.getSystemToken(config.appId))
            .header("Accept", "application/json")
            .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
            .body(request)
            .responseString()

        return result.fold(
            { json ->
                val feil = jsonNode(json).get("feil").get(fnr.toString())?.toString()
                if (feil == null) {
                    val personFunnet = jsonNode(json).get("personer").get(fnr.toString()).toString()
                    deserialize<HentKontaktinformasjonRepsonse>(personFunnet).toKontaktinformasjon()
                } else {
                    val errorMessage = "Feil ved henting av digital kontaktinformasjon. Årsak=$feil. "
                    log.error(errorMessage + "Se sikkerlogg for mer kontekst.")
                    sikkerLogg.error(errorMessage + "Fnr: $fnr")
                    KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
                }
            },
            {
                val errorMessage =
                    "Feil ved henting av digital kontaktinformasjon. Status=${response.statusCode} Body=${
                        String(response.data)
                    }"
                if (response.statusCode == 500) {
                    // Eksempel json-response: `{"melding":"Det oppsto en feil ved kall til Maskinporten"}`
                    log.warn(errorMessage + "Se sikkerlogg for mer kontekst.", it)
                    sikkerLogg.warn(errorMessage + "Fnr: $fnr", it)
                } else {
                    log.error(errorMessage + "Se sikkerlogg for mer kontekst.", it)
                    sikkerLogg.error(errorMessage + "Fnr: $fnr", it)
                }
                KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
            },
        )
    }
}

data class HentKontaktinformasjonRequest(
    val personidenter: List<String>,
)

private data class HentKontaktinformasjonRepsonse(
    val personident: String,
    val aktiv: Boolean,
    val reservert: Boolean?,
    val kanVarsles: Boolean?,
    val epostadresse: String?,
    val mobiltelefonnummer: String?,
    val spraak: String?,
) {
    fun toKontaktinformasjon(): Either<KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.BrukerErIkkeRegistrert, Kontaktinformasjon> {
        return if (aktiv) {
            Kontaktinformasjon(
                epostadresse = epostadresse,
                mobiltelefonnummer = mobiltelefonnummer,
                reservert = reservert,
                kanVarsles = kanVarsles,
                språk = spraak,
            ).right()
        } else {
            KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.BrukerErIkkeRegistrert.left()
        }
    }
}
