package no.nav.su.se.bakover.client.krr

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.benmanes.caffeine.cache.Cache
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory
import java.time.Duration

internal const val PERSONER_PATH = "/rest/v1/personer"

/**
 * @see https://digdir-krr-proxy.intern.dev.nav.no/swagger-ui/index.html#/
 */
class KontaktOgReservasjonsregisterClient(
    val config: ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig,
    val azure: AzureAd,
    suMetrics: SuMetrics,
    private val krrCache: Cache<Fnr, Kontaktinformasjon> = newCache(
        cacheName = "kontaktinfo",
        expireAfterWrite = Duration.ofMinutes(30),
        suMetrics = suMetrics,
    ),
) : KontaktOgReservasjonsregister {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentKontaktinformasjon(fnr: Fnr): Either<KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon, Kontaktinformasjon> {
        krrCache.getIfPresent(fnr)?.let { cachedKontaktinfo ->
            return cachedKontaktinfo.right()
        }
        val request = serialize(
            HentKontaktinformasjonRequest(
                personidenter = listOf(fnr.toString()),
            ),
        )
        val (_, response, result) = "${config.url}$PERSONER_PATH".httpPost()
            .authentication().bearer(azure.getSystemToken(config.appId))
            .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
            .body(request)
            .responseString()

        val svar = result.fold(
            { json ->

                val jsonNode = jsonNode(json)
                when {
                    jsonNode.has("personer") -> {
                        val personer = jsonNode.get("personer")
                        if (personer.has(fnr.toString())) {
                            val personFunnet = personer.get(fnr.toString()).toString()
                            deserialize<HentKontaktinformasjonRepsonse>(personFunnet).toKontaktinformasjon()
                        } else {
                            val errorMessage = "Feil ved henting av digital kontaktinformasjon. Årsak=mangler fnr i objekt."
                            log.error(errorMessage, RuntimeException("Trigger stacktrace"))
                            sikkerLogg.error(errorMessage + "obj: $jsonNode")
                            KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
                        }
                    }

                    jsonNode.has("feil") -> {
                        val feil = jsonNode.get("feil").get(fnr.toString())?.toString()
                        val errorMessage = "Feil ved henting av digital kontaktinformasjon. Årsak=$feil."
                        log.error(errorMessage + "Se sikkerlogg for mer kontekst.", RuntimeException("Trigger stacktrace"))
                        sikkerLogg.error(errorMessage + "Fnr: $fnr")
                        KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
                    }

                    else -> {
                        log.error("""Feil under deserialisering av respons KRR. Mangler felter "personer" og "feil"""".trimIndent(), RuntimeException("Trigger stacktrace"))
                        KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
                    }
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
        return svar.onRight { krrCache.put(fnr, it) }
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
