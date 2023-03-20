package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.common.ApplicationConfig.ClientsConfig.SkatteetatenConfig
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.erI
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.toRange
import no.nav.su.se.bakover.common.token.JwtToken
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear.Companion.hentMestGyldigeSkattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.domain.skatt.Stadie
import no.nav.su.se.bakover.domain.skatt.toYearRange
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Year

/**
 * https://sigrun-ske.dev.adeo.no/swagger-ui/index.html#/
 * https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/data_spesifisertsummertskattegrunnlag.html
 * https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/data_summertskattegrunnlag2021
 * https://github.com/navikt/sigrun/pull/50
 */
internal class SkatteClient(
    private val skatteetatenConfig: SkatteetatenConfig,
    private val personOppslag: PersonOppslag,
    private val hentBrukerToken: () -> JwtToken.BrukerToken = { JwtToken.BrukerToken.fraMdc() },
    private val azureAd: AzureAd,
    private val fnrOgListeAvSkattegrunnlagCache: Cache<Fnr, List<SamletSkattegrunnlagResponseMedYear>> = newCache(
        cacheName = "fnrTilSkattegrunnlag",
        expireAfterWrite = Duration.ofDays(1),
    ),
) : Skatteoppslag {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        år: Year,
    ): Either<SkatteoppslagFeil, SamletSkattegrunnlagResponseMedYear> = personOppslag.sjekkTilgangTilPerson(fnr).map {
        hentSkatteMeldingFraCacheForÅrEllerSøkOpp(fnr, år.toRange()).single()
    }.mapLeft {
        SkatteoppslagFeil.PersonFeil(it)
    }

    override fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange,
    ): Either<SkatteoppslagFeil, List<SamletSkattegrunnlagResponseMedYear>> =
        personOppslag.sjekkTilgangTilPerson(fnr).map {
            hentSkatteMeldingFraCacheForÅrEllerSøkOpp(fnr, yearRange)
        }.mapLeft {
            SkatteoppslagFeil.PersonFeil(it)
        }

    private fun hentSkatteMeldingFraCacheForÅrEllerSøkOpp(
        fnr: Fnr,
        yearRange: YearRange,
    ): List<SamletSkattegrunnlagResponseMedYear> {
        return fnrOgListeAvSkattegrunnlagCache.getIfPresent(fnr)?.let { skatteListe ->
            if (skatteListe.toYearRange().inneholder(yearRange)) {
                skatteListe.filter { it.år erI yearRange }
            } else {
                hentSkattemeldingForÅrsperiodeOgLeggInnICache(fnr, yearRange)
            }
        } ?: hentSkattemeldingForÅrsperiodeOgLeggInnICache(fnr, yearRange)
    }

    private fun hentSkattemeldingForÅrsperiodeOgLeggInnICache(
        fnr: Fnr,
        yearRange: YearRange,
    ): List<SamletSkattegrunnlagResponseMedYear> {
        return hentForÅrsperiode(fnr, yearRange).let { skatteliste ->
            skatteliste.hentMestGyldigeSkattegrunnlag().map {
                fnrOgListeAvSkattegrunnlagCache.put(fnr, skatteliste)
            }
            skatteliste
        }
    }

    private fun hentForÅrsperiode(fnr: Fnr, yearRange: YearRange): List<SamletSkattegrunnlagResponseMedYear> {
        val correlationId = CorrelationId.getOrCreateCorrelationIdFromThreadLocal()
        val token = azureAd.onBehalfOfToken(hentBrukerToken().toString(), "srvsigrun")

        return runBlocking {
            withContext(Dispatchers.IO) {
                yearRange.map {
                    async(Dispatchers.IO) {
                        hentSkattedataForAlleStadier(fnr, it, token, correlationId)
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun hentSkattedataForAlleStadier(
        fnr: Fnr,
        inntektsÅr: Year,
        token: String,
        correlationId: CorrelationId,
    ): SamletSkattegrunnlagResponseMedYear {
        return withContext(Dispatchers.IO) {
            val samletSkattFastsatt = async(Dispatchers.IO) {
                Pair(
                    hentSamletSkattegrunnlagFraSkatt(fnr, inntektsÅr, Stadie.FASTSATT, token, correlationId),
                    Stadie.FASTSATT,
                )
            }
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
        token: String,
        correlationId: CorrelationId,
    ): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create("${skatteetatenConfig.apiBaseUrl}/api/spesifisertsummertskattegrunnlag"))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("x-naturligident", fnr.toString())
            .header("x-inntektsaar", inntektsÅr.toString())
            .header("x-rettighetspakke", skatteetatenConfig.rettighetspakke)
            .header("Nav-Call-Id", correlationId.toString())
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
    ): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
        fun logError(throwable: Throwable? = RuntimeException("Genererer en stacktrace.")) {
            log.error("Kall mot Sigrun/skatteetatens api feilet med statuskode ${response.statusCode()}. Se sikkerlogg.")
            sikkerLogg.error(
                "Kall mot Sigrun/skatteetatens api feilet med statuskode ${response.statusCode()}, Fnr: $fnr, Inntektsår: $inntektsÅr, Stadie: $stadie og følgende feil: ${response.body()}. " +
                    "Request $getRequest er forespørselen mot skatteetaten som feilet.",
                throwable,
            )
        }
        return when (val status = response.statusCode()) {
            200 -> SpesifisertSummertSkattegrunnlagResponseJson.fromJson(
                json = response.body(),
                fnr = fnr,
                inntektsår = inntektsÅr,
                stadie = stadie,
            )

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
