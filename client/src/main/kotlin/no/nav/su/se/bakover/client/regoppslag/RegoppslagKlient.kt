package no.nav.su.se.bakover.client.regoppslag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.benmanes.caffeine.cache.Cache
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.isServerError
import com.github.kittinunf.fuel.core.isSuccessful
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.client.person.Behandlingsnummer
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
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
    private val azureAd: AzureAd,
    private val url: String,
    private val scope: String,
    private val hentBrukerToken: () -> JwtToken.BrukerToken = {
        JwtToken.BrukerToken.fraCoroutineContext()
    },
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
                logger.info("Fant cachet mottakeradresse")
                regoppslagCache.right()
            } else {
                logger.info("Ingen cachet mottakeradresse funnet. Henter fra regoppslag")
                val brukerToken = hentBrukerToken()
                val (_, response, result) = "$url/rest/postadresse"
                    .httpPost()
                    .authentication().bearer(azureAd.onBehalfOfToken(brukerToken.value, scope))
                    .header("behandlingsnummer", Behandlingsnummer.fraSakstype(sakType).value)
                    .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    .header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    .body(serialize(RegoppslagRequest(ident.toString(), Tema.SUPPLERENDE_STØNAD.value)))
                    .responseString()

                håndterSvar(response, result.fold({ it.right() }, { it.left() })).also {
                    it.map { adresse -> cache.put(cacheKey, adresse) }
                }
            }
        } catch (e: Exception) {
            logger.error("Uhåndtert exception fra regoppslag ", e)
            RegoppslagFeil.UkjentFeil(500, "Ukjent feil oppsto ved uthenting av mottakers adresse fra regoppslag").left()
        }

    private fun håndterSvar(
        response: Response,
        result: Either<Exception, String>,
    ): Either<RegoppslagFeil, RegoppslagResponseDTO> {
        return result.fold(
            ifLeft = { error ->
                when {
                    response.statusCode == HttpStatusCode.NotFound.value -> {
                        logger.info("Bruker har ukjent adresse ")
                        RegoppslagFeil.IkkeFunnet.left()
                    }

                    response.statusCode == HttpStatusCode.Gone.value -> {
                        logger.warn("Person er død og har ukjent adresse: ${response.statusCode}")
                        RegoppslagFeil.PersonErDød.left()
                    }

                    response.statusCode == HttpStatusCode.Unauthorized.value || response.isServerError -> {
                        logger.error("Feil fra regoppslag : ${response.statusCode}", error)
                        RegoppslagFeil.UkjentFeil(
                            response.statusCode,
                            "Ukjent feil oppsto ved uthenting av mottakers adresse fra regoppslag",
                        ).left()
                    }

                    else -> {
                        logger.error("Uhåndtert feil fra regoppslag : ${response.statusCode}", error)
                        RegoppslagFeil.UkjentFeil(
                            response.statusCode,
                            "Ukjent feil oppsto ved uthenting av mottakers adresse fra regoppslag",
                        ).left()
                    }
                }
            },
            ifRight = { body ->
                if (!response.isSuccessful) {
                    logger.error("Uhåndtert feil fra regoppslag : ${response.statusCode}, body=$body")
                    RegoppslagFeil.UkjentFeil(
                        response.statusCode,
                        "Ukjent feil oppsto ved uthenting av mottakers adresse fra regoppslag",
                    ).left()
                } else {
                    Either.catch { deserialize<RegoppslagResponseDTO>(body) }.fold(
                        ifLeft = {
                            logger.error("Kunne ikke deserialisere respons fra regoppslag ", it)
                            RegoppslagFeil.UkjentFeil(
                                HttpStatusCode.InternalServerError.value,
                                "Ukjent feil oppsto ved uthenting av mottakers adresse fra regoppslag",
                            ).left()
                        },
                        ifRight = { it.right() },
                    )
                }
            },
        )
    }
}
