package no.nav.su.se.bakover.client.kodeverk

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.kodeverk.Kodeverk.CouldNotGetKode
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal const val KODEVERK_POSTSTED_PATH = "/api/v1/kodeverk/Postnummer/koder/betydninger"
internal const val KODEVERK_KOMMUNENAVN_PATH = "/api/v1/kodeverk/Kommuner/koder/betydninger"

/**
 * Dokumentasjon: https://navikt.github.io/felleskodeverk/
 * Swagger: https://kodeverk.dev.intern.nav.no/swagger-ui/index.html#/kodeverk/betydning
 * Eksternt repo: https://github.com/navikt/felleskodeverk
 */
class KodeverkHttpClient(
    val baseUrl: String,
    private val consumerId: String,
    private val azureAd: AzureAd,
    private val kodeverkClientId: String,
) : Kodeverk {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun hentPoststed(postnummer: String, token: JwtToken): Either<CouldNotGetKode, String?> {
        return hentKodebetydning(KODEVERK_POSTSTED_PATH, postnummer, token)
    }

    override fun hentKommunenavn(kommunenummer: String, token: JwtToken): Either<CouldNotGetKode, String?> {
        return hentKodebetydning(KODEVERK_KOMMUNENAVN_PATH, kommunenummer, token)
    }

    private fun hentKodebetydning(path: String, value: String, token: JwtToken): Either<CouldNotGetKode, String?> {
        val bearerToken = token.toBearerToken()
        val (_, response, result) = "$baseUrl$path".httpGet()
            .header("Content-Type", "application/json")
            .header("Nav-Call-Id", getOrCreateCorrelationIdFromThreadLocal())
            .header("Nav-Consumer-Id", consumerId)
            .header("Authorization", "Bearer $bearerToken")
            .apply {
                parameters = listOf("ekskluderUgyldige" to "true", "spraak" to "nb")
            }
            .responseString()

        return result.fold(
            { json ->
                Either.catch {
                    deserialize<KodeverkResponse>(json).betydninger.getOrDefault(value, emptyList())
                        .map { it.beskrivelser.nb.term }.firstOrNull().right()
                }.getOrElse {
                    log.error("Feil i deserialisering av kodeverksresponse. response body={}", json, it)
                    return CouldNotGetKode.left()
                }
            },
            {
                log.error(
                    "Feil i kallet mot kodeverk. status={} body={}",
                    response.statusCode,
                    response.body().asString("application/json"),
                    it,
                )
                CouldNotGetKode.left()
            },
        )
    }

    private fun JwtToken.toBearerToken(): String = when (this) {
        is JwtToken.BrukerToken -> azureAd.onBehalfOfToken(this.value, kodeverkClientId)
        is JwtToken.SystemToken -> azureAd.getSystemToken(kodeverkClientId)
    }

    data class KodeverkResponse(
        val betydninger: Map<String, List<Betydning>>,
    )

    data class Betydning(
        val gyldigFra: String,
        val gyldigTil: String,
        val beskrivelser: Beskrivelser,
    )

    data class Beskrivelser(
        val nb: Beskrivelse,
    )

    data class Beskrivelse(
        val tekst: String,
        val term: String,
    )
}
