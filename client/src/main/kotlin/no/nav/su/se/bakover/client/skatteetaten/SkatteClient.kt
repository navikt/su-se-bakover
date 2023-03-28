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
 *
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
        val token = azureAd.onBehalfOfToken(hentBrukerToken().toString(), skatteetatenConfig.clientId)

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
    ): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
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
