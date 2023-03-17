package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.ApplicationConfig.ClientsConfig.SkatteetatenConfig
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.token.JwtToken
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.domain.skatt.Stadie
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Year

/**
 * https://sigrun-ske.dev.adeo.no/swagger-ui/index.html#/
 * https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/data_spesifisertsummertskattegrunnlag.html
 * https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/data_summertskattegrunnlag2021
 * https://github.com/navikt/sigrun/pull/50
 */
class SkatteClient(
    private val skatteetatenConfig: SkatteetatenConfig,
    private val clock: Clock,
    private val hentBrukerToken: () -> JwtToken.BrukerToken = {
        JwtToken.BrukerToken.fraMdc()
    },
    private val azureAd: AzureAd,
) : Skatteoppslag {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        inntektsÅr: Year,
    ): SamletSkattegrunnlagResponseMedYear {
        return runBlocking { hentSkattedataForAlleStadier(fnr, inntektsÅr) }
    }

    override fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange,
    ): List<SamletSkattegrunnlagResponseMedYear> {
        return runBlocking {
            withContext(Dispatchers.IO) {
                yearRange.map {
                    async(Dispatchers.IO) {
                        hentSkattedataForAlleStadier(fnr, it)
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun hentSkattedataForAlleStadier(
        fnr: Fnr,
        inntektsÅr: Year,
    ): SamletSkattegrunnlagResponseMedYear {
        return withContext(Dispatchers.IO) {
            val samletSkattFastsatt = async(Dispatchers.IO) {
                Pair(hentSamletSkattegrunnlagFraSkatt(fnr, inntektsÅr, Stadie.FASTSATT), Stadie.FASTSATT)
            }
            val samletSkattOppgjør = async(Dispatchers.IO) {
                Pair(hentSamletSkattegrunnlagFraSkatt(fnr, inntektsÅr, Stadie.OPPGJØR), Stadie.OPPGJØR)
            }
            val samletSkattUtkast = async(Dispatchers.IO) {
                Pair(hentSamletSkattegrunnlagFraSkatt(fnr, inntektsÅr, Stadie.UTKAST), Stadie.UTKAST)
            }
            listOf(samletSkattFastsatt, samletSkattOppgjør, samletSkattUtkast)
                .awaitAll().let {
                    SamletSkattegrunnlagResponseMedYear(
                        it.map { SamletSkattegrunnlagResponseMedStadie(it.first, it.second) },
                        inntektsÅr,
                    )
                }
        }
    }


    private fun hentSamletSkattegrunnlagFraSkatt(
        fnr: Fnr,
        inntektsÅr: Year,
        stadie: Stadie,
    ): Either<SkatteoppslagFeil, Skattegrunnlag> {
        val token = azureAd.onBehalfOfToken(hentBrukerToken().toString(), "srvsigrun")
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("${skatteetatenConfig.apiBaseUrl}/api/spesifisertsummertskattegrunnlag"))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("x-naturligident", fnr.toString())
            .header("x-inntektsaar", inntektsÅr.toString())
            .header("x-rettighetspakke", skatteetatenConfig.rettighetspakke)
            .header("Nav-Call-Id", CorrelationId.getOrCreateCorrelationIdFromThreadLocal().toString())
            .header("Nav-Consumer-Id", skatteetatenConfig.consumerId)
            .header("stadie", stadie.verdi)
            .GET()
            .build()

        return Either.catch {
            client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        }.mapLeft {
            log.warn("Fikk en exception ${it.message} i henting av data fra Sigrun/skatteetaten. Se sikkerlogg.", it)
            sikkerLogg.warn(
                "Fikk en exception ${it.message} i henting av data fra Sigrun/skatteetaten. " +
                    "Request $getRequest er forespørselen mot skatteetaten som feilet.",
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
        inntektsÅr: Year,
        stadie: Stadie,
    ): Either<SkatteoppslagFeil, Skattegrunnlag> {
        fun logError(throwable: Throwable? = RuntimeException("Genererer en stacktrace.")) {
            log.error("Kall mot Sigrun/skatteetatens api feilet med statuskode ${response.statusCode()}. Se sikkerlogg.")
            sikkerLogg.error(
                "Kall mot Sigrun/skatteetatens api feilet med statuskode ${response.statusCode()} og følgende feil: ${response.body()}. " +
                    "Request $getRequest er forespørselen mot skatteetaten som feilet.",
                throwable,
            )
        }
        return when (val status = response.statusCode()) {
            200 -> SpesifisertSummertSkattegrunnlagResponseJson.fromJson(
                json = response.body(),
                clock = clock,
                fnr = fnr,
                inntektsår = inntektsÅr,
                stadie = stadie,
            ).mapLeft { it }
                .map { it }

            400 -> SkatteoppslagFeil.UkjentFeil(IllegalArgumentException("Fikk 400 fra Sigrun.")).also {
                logError(it.throwable)
            }.left()

            403 -> SkatteoppslagFeil.ManglerRettigheter.also {
                logError()
            }.left()

            404 -> SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.also {
                logError()
            }.left()

            500 -> SkatteoppslagFeil.UkjentFeil(IllegalArgumentException("Fikk 500 fra Sigrun.")).also {
                logError(it.throwable)
            }.left()

            503 -> SkatteoppslagFeil.Nettverksfeil(IllegalArgumentException("Fikk 503 fra Sigrun.")).also {
                logError(it.throwable)
            }.left()

            else -> SkatteoppslagFeil.UkjentFeil(IllegalArgumentException("Fikk uforventet statuskode fra Sigrun: $status"))
                .also {
                    logError(it.throwable)
                }.left()
        }
    }
}
