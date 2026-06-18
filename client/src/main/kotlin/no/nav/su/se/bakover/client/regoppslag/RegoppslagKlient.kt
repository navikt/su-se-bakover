package no.nav.su.se.bakover.client.regoppslag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.benmanes.caffeine.cache.Cache
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.person.Fnr
import org.slf4j.LoggerFactory
import java.time.Duration

data class RegoppslagRequest(
    val ident: String,
    val tema: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RegoppslagResponseDTO(
    val navn: String,
    val adresse: Adresse,
) {
    data class Adresse(
        val type: AdresseType,
        val adresseKilde: AdresseKilde?,
        val adresselinje1: String?,
        val adresselinje2: String?,
        val adresselinje3: String?,
        val postnummer: String?,
        val poststed: String?,
        val landkode: String,
        val land: String,
    )

    enum class AdresseType {
        NORSKPOSTADRESSE,
        UTENLANDSKPOSTADRESSE,
    }

    enum class AdresseKilde {
        BOSTEDSADRESSE,
        OPPHOLDSADRESSE,
        KONTAKTADRESSE,
        DELTBOSTED,
        KONTAKTINFORMASJONFORDØDSBO,
        ENHETPOSTADRESSE,
        ENHETFORRETNINGSADRESSE,
    }
}

sealed class RegoppslagFeil {
    data object IkkeFunnet : RegoppslagFeil()
    data object PersonErDød : RegoppslagFeil()
    data class UkjentFeil(val statusCode: Int, val detail: String) : RegoppslagFeil()
}

interface RegoppslagKlient {
    suspend fun hentMottakerAdresse(
        sakType: Sakstype,
        ident: Fnr,
    ): Either<RegoppslagFeil, RegoppslagResponseDTO>
}

internal class RegoppslagKlientImpl(
    private val httpClient: HttpClient,
    private val url: String,
    suMetrics: SuMetrics,
    private val cache: Cache<String, RegoppslagResponseDTO> = newCache(
        cacheName = "regoppslag",
        expireAfterWrite = Duration.ofMinutes(15),
        suMetrics = suMetrics,
    ),
) : RegoppslagKlient {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun hentMottakerAdresse(
        sakType: Sakstype,
        ident: Fnr,
    ): Either<RegoppslagFeil, RegoppslagResponseDTO> =
        try {
            val cacheKey = ident.toString()
            val regoppslagCache = cache.getIfPresent(cacheKey)

            if (regoppslagCache != null) {
                logger.info("Fant cachet mottakeradresse for ident=$cacheKey")
                regoppslagCache.right()
            } else {
                logger.info("Ingen cachet mottakeradresse funnet for ident=$cacheKey. Henter fra regoppslag")

                httpClient.post("$url/rest/postadresse") {
                    contentType(ContentType.Application.Json)
                    setBody(RegoppslagRequest(ident.toString(), Tema.SUPPLERENDE_STØNAD.value))
                }.body<RegoppslagResponseDTO>()
                    .also {
                        cache.put(cacheKey, it)
                    }
                    .right()
            }
        } catch (re: ResponseException) {
            when (re.response.status) {
                HttpStatusCode.NotFound -> {
                    logger.info("Bruker har ukjent adresse for ident=$ident")
                    RegoppslagFeil.IkkeFunnet.left()
                }
                HttpStatusCode.Gone -> {
                    logger.warn("Person er død og har ukjent adresse for ident=$ident: ${re.response.status}")
                    RegoppslagFeil.PersonErDød.left()
                }
                else -> {
                    logger.error("Uhåndtert feil fra regoppslag for ident=$ident: ${re.response.status}")
                    RegoppslagFeil.UkjentFeil(re.response.status.value, "Ukjent feil oppsto ved uthenting av mottakers adresse fra regoppslag").left()
                }
            }
        } catch (e: Exception) {
            logger.error("Uhåndtert exception fra regoppslag for ident=$ident", e)
            RegoppslagFeil.UkjentFeil(500, "Ukjent feil oppsto ved uthenting av mottakers adresse fra regoppslag").left()
        }
}
