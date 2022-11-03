package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.token.JwtToken
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.journalpost.ErTilknyttetSak
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.JournalpostClientMetrics
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkKontrollnotatMottatt
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.domain.sak.Saksnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// docs: https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning#
// https://confluence.adeo.no/display/BOA/Query%3A+journalpost
internal class JournalpostHttpClient(
    private val safConfig: ApplicationConfig.ClientsConfig.SafConfig,
    private val azureAd: AzureAd,
    private val sts: TokenOppslag,
    private val metrics: JournalpostClientMetrics,
    private val erTilknyttetSakCache: Cache<JournalpostId, ErTilknyttetSak> = newCache(cacheName = "erTilknyttetSak", expireAfterWrite = Duration.ofHours(1)),
) : JournalpostClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val graphQLUrl: URI = URI.create("${safConfig.url}/graphql")
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override suspend fun erTilknyttetSak(
        journalpostId: JournalpostId,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak> {
        erTilknyttetSakCache.getIfPresent(journalpostId)?.let { return it.right() }
        val request = GraphQLQuery<HentJournalpostHttpResponse>(
            lagRequest(
                query = "/hentJournalpostQuery.graphql",
                datafelter = "/erJournalpostTilknyttetSakDatafelter.graphql",
            ),
            HentJournalpostVariables(journalpostId.toString()),
        )
        val brukerToken = JwtToken.BrukerToken.fraMdc()

        return gqlRequest(
            request = request,
            token = azureAd.onBehalfOfToken(
                originalToken = brukerToken.value,
                otherAppId = safConfig.clientId,
            ),
        ).fold(
            {
                when (it) {
                    is GraphQLApiFeil.HttpFeil.BadRequest -> when (it.msg) {
                        "journalpostId er en ikke-numerisk verdi." -> KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost
                        else -> KunneIkkeSjekkeTilknytningTilSak.UgyldigInput
                    }
                    is GraphQLApiFeil.HttpFeil.Forbidden -> KunneIkkeSjekkeTilknytningTilSak.IkkeTilgang
                    is GraphQLApiFeil.HttpFeil.NotFound -> KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost
                    is GraphQLApiFeil.HttpFeil.ServerError -> KunneIkkeSjekkeTilknytningTilSak.TekniskFeil
                    is GraphQLApiFeil.HttpFeil.Ukjent -> KunneIkkeSjekkeTilknytningTilSak.Ukjent
                    is GraphQLApiFeil.TekniskFeil -> KunneIkkeSjekkeTilknytningTilSak.TekniskFeil
                }.left()
            },
            { response ->
                response.data!!.journalpost?.let {
                    return (
                        if (it.sak?.fagsakId == saksnummer.toString()) {
                            ErTilknyttetSak.Ja
                        } else {
                            ErTilknyttetSak.Nei
                        }
                        ).also {
                        erTilknyttetSakCache.put(journalpostId, it)
                    }.right()
                } ?: return KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost.left()
            },
        )
    }

    override fun kontrollnotatMotatt(
        saksnummer: Saksnummer,
        periode: DatoIntervall,
    ): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt> {
        val kontrollnotatTittel = "NAV SU Kontrollnotat"
        val dokumentasjonAvOppfølgingsamtaleTittel = "Dokumentasjon av oppfølgingssamtale"

        fun String.inneholder(string: String): Boolean {
            return this.contains(string)
        }

        fun String.toBenyttetSkjemaMetric(): JournalpostClientMetrics.BenyttetSkjema {
            if (this.inneholder(kontrollnotatTittel)) return JournalpostClientMetrics.BenyttetSkjema.NAV_SU_KONTROLLNOTAT
            if (this.inneholder(dokumentasjonAvOppfølgingsamtaleTittel)) return JournalpostClientMetrics.BenyttetSkjema.DOKUMENTASJON_AV_OPPFØLGINGSSAMTALE
            throw IllegalArgumentException("Ukjent og uønsket tittel")
        }

        val request = GraphQLQuery<HentDokumentoversiktFagsakHttpResponse>(
            query = lagRequest(
                query = "/dokumentoversiktFagsakQuery.graphql",
                datafelter = "/hentMottattKontrollnotatDatafelter.graphql",
            ),
            variables = HentJournalpostForFagsakVariables(
                fagsakId = saksnummer.toString(),
                fagsaksystem = "SUPSTONAD",
                fraDato = periode.fraOgMed.toString(),
                tema = "SUP",
                journalposttyper = listOf("I"),
                journalstatuser = listOf("JOURNALFOERT"),
                foerste = 100,
            ),
        )
        return runBlocking {
            gqlRequest(
                request = request,
                token = sts.token().value,
            ).mapLeft { error ->
                KunneIkkeSjekkKontrollnotatMottatt(error).also { log.error("Feil: $it ved henting av journalposter for saksnummer:$saksnummer") }
            }.map { response ->
                response.data!!.dokumentoversiktFagsak.journalposter
                    .toDomain()
                    .sortedBy { it.datoOpprettet }
                    .lastOrNull {
                        periode.inneholder(it.datoOpprettet) && (
                            it.tittel.inneholder(kontrollnotatTittel) || it.tittel.inneholder(
                                dokumentasjonAvOppfølgingsamtaleTittel,
                            )
                            )
                    }?.also { metrics.inkrementerBenyttetSkjema(it.tittel.toBenyttetSkjemaMetric()) }
                    ?.let { ErKontrollNotatMottatt.Ja(it) } ?: ErKontrollNotatMottatt.Nei
            }
        }
    }

    private fun lagRequest(
        query: String,
        datafelter: String,
    ): String {
        val gqlQuery = javaClass.getResource(query)
            ?.readText()
            ?: throw IllegalArgumentException("Fant ikke fil med navn: $query")
        val data = javaClass.getResource(datafelter)
            ?.readText()
            ?: throw IllegalArgumentException("Fant ikke fil med navn: $query")
        return gqlQuery.replace("<<DATAFELTER>>", data)
    }

    private suspend inline fun <reified Response : GraphQLHttpResponse> gqlRequest(
        request: GraphQLQuery<Response>,
        token: String,
    ): Either<GraphQLApiFeil, Response> {
        return Either.catch {
            HttpRequest.newBuilder(graphQLUrl)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Nav-Consumer-Id", "su-se-bakover")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build()
                .let { httpRequest ->
                    client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).await()
                        .let { httpResponse ->
                            if (httpResponse.isSuccess()) {
                                // GraphQL returnerer 200 for det meste
                                objectMapper.readValue<Response>(httpResponse.body()).let { response ->
                                    if (response.hasErrors()) {
                                        return response.mapGraphQLHttpFeil(request)
                                            .also { log.warn("Feil: $it ved kall mot: $graphQLUrl") }
                                            .left()
                                    } else {
                                        response
                                    }
                                }
                            } else {
                                // ting som ikke når helt til GraphQL - typisk 401 uten token eller lignende
                                return GraphQLApiFeil.HttpFeil.Ukjent(
                                    request,
                                    """Status: ${httpResponse.statusCode()}, Body:${httpResponse.body()}""",
                                ).also { log.warn("Feil: $it ved kall mot: $graphQLUrl") }
                                    .left()
                            }
                        }
                }
        }.mapLeft { throwable ->
            GraphQLApiFeil.TekniskFeil(request, throwable.toString())
                .also { sikkerLogg.warn("Feil: $it ved kall mot: $graphQLUrl") }
        }
    }

    sealed class GraphQLApiFeil {

        data class TekniskFeil(val request: Any, val msg: String) : GraphQLApiFeil()
        sealed class HttpFeil : GraphQLApiFeil() {
            data class NotFound(val request: Any, val msg: String) : HttpFeil()
            data class ServerError(val request: Any, val msg: String) : HttpFeil()
            data class BadRequest(val request: Any, val msg: String) : HttpFeil()
            data class Forbidden(val request: Any, val msg: String) : HttpFeil()

            data class Ukjent(val request: Any, val msg: String) : HttpFeil()
        }
    }
}

internal abstract class GraphQLHttpResponse {
    abstract val data: Any?
    abstract val errors: List<Error>?
    fun hasErrors(): Boolean {
        return errors?.isNotEmpty() ?: false
    }

    // https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning#safUtviklerveiledning-Feilh%C3%A5ndtering
    fun mapGraphQLHttpFeil(request: Any): JournalpostHttpClient.GraphQLApiFeil {
        return errors.orEmpty().map { error ->
            when (error.extensions?.code) {
                "forbidden" -> JournalpostHttpClient.GraphQLApiFeil.HttpFeil.Forbidden(request, error.message)
                "not_found" -> JournalpostHttpClient.GraphQLApiFeil.HttpFeil.NotFound(request, error.message)
                "bad_request" -> JournalpostHttpClient.GraphQLApiFeil.HttpFeil.BadRequest(request, error.message)
                "server_error" -> JournalpostHttpClient.GraphQLApiFeil.HttpFeil.ServerError(request, error.message)
                else -> JournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent(request, error.message)
            }
        }.first()
    }
}

internal data class GraphQLQuery<ResponseType>(
    val query: String,
    val variables: Any,
)

internal data class Error(
    val message: String,
    val path: List<String>?,
    val extensions: Extensions?,
)

internal data class Extensions(
    val code: String?,
    val classification: String,
)
