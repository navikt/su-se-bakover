package vilkår.skatt.infrastructure.client

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.left
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SkatteetatenConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.skatt.domain.SamletSkattegrunnlagForÅr
import vilkår.skatt.domain.SamletSkattegrunnlagForÅrOgStadie
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.skatt.domain.Skatteoppslag
import vilkår.skatt.domain.Stadie
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Year

/**
 * https://sigrun-ske.dev.adeo.no/swagger-ui/index.html#/
 * https://skatteetaten.github.io/api-dokumentasjon/api/spesifisertsummertskattegrunnlag
 * https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/data_summertskattegrunnlag2021
 * https://github.com/navikt/sigrun/pull/50
 *
 */
class SkatteClient(
    private val skatteetatenConfig: SkatteetatenConfig,
    private val hentBrukerToken: () -> JwtToken.BrukerToken = { JwtToken.BrukerToken.fraMdc() },
    private val azureAd: AzureAd,
) : Skatteoppslag {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        år: Year,
    ): SamletSkattegrunnlagForÅr = hentForÅrsperiode(fnr, år.toRange()).single()

    override fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange,
    ): NonEmptyList<SamletSkattegrunnlagForÅr> = hentForÅrsperiode(fnr, yearRange)

    private fun hentForÅrsperiode(fnr: Fnr, yearRange: YearRange): NonEmptyList<SamletSkattegrunnlagForÅr> {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()
        val token = azureAd.onBehalfOfToken(hentBrukerToken().toString(), skatteetatenConfig.clientId)

        return runBlocking {
            withContext(Dispatchers.IO) {
                yearRange.map {
                    async(Dispatchers.IO) {
                        hentSkattedataForAlleStadier(fnr, it, token, correlationId)
                    }
                }.awaitAll().toNonEmptyList()
            }
        }
    }

    private suspend fun hentSkattedataForAlleStadier(
        fnr: Fnr,
        inntektsÅr: Year,
        token: String,
        correlationId: CorrelationId,
    ): SamletSkattegrunnlagForÅr {
        return withContext(Dispatchers.IO) {
            val samletSkattOppgjør = async(Dispatchers.IO) {
                Pair(
                    hentSamletSkattegrunnlagFraSkatt(fnr, inntektsÅr, Stadie.OPPGJØR, token, correlationId),
                    Stadie.OPPGJØR,
                )
            }
            val samletSkattUtkast = async(Dispatchers.IO) {
                Pair(
                    hentSamletSkattegrunnlagFraSkatt(fnr, inntektsÅr, Stadie.UTKAST, token, correlationId),
                    Stadie.UTKAST,
                )
            }
            listOf(samletSkattOppgjør, samletSkattUtkast)
                .awaitAll().let {
                    SamletSkattegrunnlagForÅr(
                        utkast = it[1].let {
                            SamletSkattegrunnlagForÅrOgStadie.Utkast(
                                oppslag = it.first.mapLeft {
                                    it.tilKunneIkkeHenteSkattemelding()
                                },
                                inntektsår = inntektsÅr,
                            )
                        },
                        oppgjør = it.first().let {
                            SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                                oppslag = it.first.mapLeft {
                                    it.tilKunneIkkeHenteSkattemelding()
                                },
                                inntektsår = inntektsÅr,
                            )
                        },
                        år = inntektsÅr,
                    )
                }
        }
    }

    private fun hentSamletSkattegrunnlagFraSkatt(
        fnr: Fnr,
        inntektsÅr: Year,
        stadie: Stadie,
        token: String,
        correlationId: CorrelationId,
    ): Either<SkatteoppslagFeil, Skattegrunnlag.SkattegrunnlagForÅr> {
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("${skatteetatenConfig.apiBaseUrl}/api/v1/spesifisertsummertskattegrunnlag"))
            .setHeader("Accept", "application/json")
            .setHeader("Authorization", "Bearer $token")
            .setHeader("Nav-Personident", fnr.toString())
            .setHeader("inntektsaar", inntektsÅr.toString())
            .setHeader("rettighetspakke", skatteetatenConfig.rettighetspakke)
            .setHeader("Nav-Call-Id", correlationId.toString())
            .setHeader("Nav-Consumer-Id", skatteetatenConfig.consumerId)
            .setHeader("stadie", stadie.verdi)
            .GET()
            .build()

        return Either.catch {
            client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        }.mapLeft {
            log.warn("Fikk en exception ${it.message} i henting av data fra Sigrun/skatteetaten. Se sikkerlogg.", it)
            sikkerLogg.warn(
                "Fikk en exception ${it.message} i henting av data fra Sigrun/skatteetaten. " +
                    "Request $getRequest er forespørselen mot skatteetaten som feilet. Headere ${getRequest.headers()}",
            )
            SkatteoppslagFeil.Nettverksfeil(it)
        }.flatMap { response ->
            handleResponse(response, getRequest, fnr, inntektsÅr, stadie)
        }
    }

    private fun handleResponse(
        response: HttpResponse<String>,
        getRequest: HttpRequest?,
        fnr: Fnr,
        inntektsår: Year,
        stadie: Stadie,
    ): Either<SkatteoppslagFeil, Skattegrunnlag.SkattegrunnlagForÅr> {
        val statusCode: Int = response.statusCode()
        val body: String = response.body()

        return when (statusCode) {
            200 -> SpesifisertSummertSkattegrunnlagResponseJson.fromJson(
                json = body,
                fnr = fnr,
                inntektsår = inntektsår,
                stadie = stadie,
            )

            else -> håndterSigrunFeil(
                statusCode = statusCode,
                body = body,
                fnr = fnr,
                inntektsår = inntektsår,
                getRequest = getRequest,
                stadie = stadie,
            ).left()
        }
    }
}
