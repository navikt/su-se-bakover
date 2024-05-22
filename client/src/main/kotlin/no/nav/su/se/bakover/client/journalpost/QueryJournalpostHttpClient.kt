package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.benmanes.caffeine.cache.Cache
import dokument.domain.journalføring.ErKontrollNotatMottatt
import dokument.domain.journalføring.ErTilknyttetSak
import dokument.domain.journalføring.Journalpost
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import dokument.domain.journalføring.KunneIkkeSjekkKontrollnotatMottatt
import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import dokument.domain.journalføring.QueryJournalpostClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// docs: https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning#
// queries: https://confluence.adeo.no/display/BOA/saf+-+Queries
// journalpost query: https://confluence.adeo.no/display/BOA/Query%3A+journalpost
internal class QueryJournalpostHttpClient(
    private val safConfig: ApplicationConfig.ClientsConfig.SafConfig,
    private val azureAd: AzureAd,
    private val suMetrics: SuMetrics,
    private val erTilknyttetSakCache: Cache<JournalpostId, ErTilknyttetSak> = newCache(
        cacheName = "erTilknyttetSak",
        expireAfterWrite = Duration.ofHours(1),
        suMetrics = suMetrics,
    ),
) : QueryJournalpostClient {
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
            getQueryFrom("/hentJournalpostQuery.graphql"),
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

    override fun hentJournalposterFor(saksnummer: Saksnummer, limit: Int): Either<KunneIkkeHenteJournalposter, List<Journalpost>> {
        val request = GraphQLQuery<HentDokumentoversiktFagsakHttpResponse>(
            query = getQueryFrom("/dokumentoversiktFagsakQuery.graphql"),
            variables = HentJournalposterForSakVariables(
                fagsak = Fagsak(fagsakId = saksnummer.toString()),
                foerste = limit,
            ),
        )
        return runBlocking {
            gqlRequest(request = request, token = azureAd.getSystemToken(safConfig.clientId)).mapLeft {
                KunneIkkeHenteJournalposter.ClientError.also { log.error("Feil: $it ved henting av journalposter for saksnummer:$saksnummer") }
            }.map {
                it.data!!.dokumentoversiktFagsak.journalposter.map {
                    Journalpost(JournalpostId(it.journalpostId!!), it.tittel!!)
                }
            }
        }
    }

    private fun getQueryFrom(path: String): String {
        val gqlQuery = javaClass.getResource(path)?.readText()
            ?: throw IllegalArgumentException("Fant ikke fil med navn: $path")

        // henter alt fra det som starter på 'query' til slutten av filen
        val regex = Regex("query[^{]*\\{.*}", RegexOption.DOT_MATCHES_ALL)
        return regex.find(gqlQuery)?.value ?: throw IllegalArgumentException("Fant ikke query i $path. $gqlQuery")
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

        val request = GraphQLQuery<HentDokumentoversiktFagsakHttpResponse>(
            query = getQueryFrom("/dokumentoversiktFagsakQuery.graphql"),
            variables = HentJournalposterForSakVariables(
                fagsak = Fagsak(fagsakId = saksnummer.toString()),
                fraDato = periode.fraOgMed.toString(),
                journalposttyper = listOf("I"),
                journalstatuser = listOf("JOURNALFOERT"),
                foerste = 100,
            ),
        )
        return runBlocking {
            gqlRequest(
                request = request,
                token = azureAd.getSystemToken(safConfig.clientId),
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
                    }
                    ?.let { ErKontrollNotatMottatt.Ja(it) } ?: ErKontrollNotatMottatt.Nei
            }
        }
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
                .POST(HttpRequest.BodyPublishers.ofString(serialize(request)))
                .build()
                .let { httpRequest ->
                    client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).await()
                        .let { httpResponse ->
                            if (httpResponse.isSuccess()) {
                                // GraphQL returnerer 200 for det meste
                                deserialize<Response>(httpResponse.body()).let { response ->
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

    sealed interface GraphQLApiFeil {

        data class TekniskFeil(val request: Any, val msg: String) : GraphQLApiFeil
        sealed interface HttpFeil : GraphQLApiFeil {
            data class NotFound(val request: Any, val msg: String) : HttpFeil
            data class ServerError(val request: Any, val msg: String) : HttpFeil
            data class BadRequest(val request: Any, val msg: String) : HttpFeil
            data class Forbidden(val request: Any, val msg: String) : HttpFeil

            data class Ukjent(val request: Any, val msg: String) : HttpFeil
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
    fun mapGraphQLHttpFeil(request: Any): QueryJournalpostHttpClient.GraphQLApiFeil {
        return errors.orEmpty().map { error ->
            when (error.extensions?.code) {
                "forbidden" -> QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.Forbidden(request, error.message)
                "not_found" -> QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.NotFound(request, error.message)
                "bad_request" -> QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.BadRequest(request, error.message)
                "server_error" -> QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.ServerError(request, error.message)
                else -> QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent(request, error.message)
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
