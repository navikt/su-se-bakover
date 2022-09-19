package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkKontrollnotatMottatt
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// docs: https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning#
internal class JournalpostHttpClient(
    private val safConfig: ApplicationConfig.ClientsConfig.SafConfig,
    private val azureAd: AzureAd,
    private val sts: TokenOppslag,
) : JournalpostClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val graphQLUrl: URI = URI.create("${safConfig.url}/graphql")
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    override fun hentFerdigstiltJournalpost(
        saksnummer: Saksnummer,
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, FerdigstiltJournalpost> {
        val request = GraphQLQuery<HentJournalpostHttpResponse>(
            lagRequest(
                query = "/hentJournalpostQuery.graphql",
                datafelter = "/hentFerdigstiltJournalpostDatafelter.graphql",
            ),
            HentJournalpostVariables(journalpostId.toString()),
        )
        return gqlRequest(
            request = request,
            token = azureAd.onBehalfOfToken(MDC.get("Authorization"), safConfig.clientId)
        ).mapLeft {
            when (it) {
                is GraphQLApiFeil.HttpFeil.BadRequest -> KunneIkkeHenteJournalpost.UgyldigInput
                is GraphQLApiFeil.HttpFeil.Forbidden -> KunneIkkeHenteJournalpost.IkkeTilgang
                is GraphQLApiFeil.HttpFeil.NotFound -> KunneIkkeHenteJournalpost.FantIkkeJournalpost
                is GraphQLApiFeil.HttpFeil.ServerError -> KunneIkkeHenteJournalpost.TekniskFeil
                is GraphQLApiFeil.HttpFeil.Ukjent -> KunneIkkeHenteJournalpost.Ukjent
                is GraphQLApiFeil.TekniskFeil -> KunneIkkeHenteJournalpost.TekniskFeil
            }
        }.flatMap { response ->
            response.data!!.journalpost.toFerdigstiltJournalpost(saksnummer)
                .mapLeft {
                    when (it) {
                        JournalpostErIkkeFerdigstilt.FantIkkeJournalpost -> KunneIkkeHenteJournalpost.FantIkkeJournalpost
                        JournalpostErIkkeFerdigstilt.JournalpostIkkeKnyttetTilSak -> KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak
                        JournalpostErIkkeFerdigstilt.JournalpostTemaErIkkeSUP -> KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP
                        JournalpostErIkkeFerdigstilt.JournalpostenErIkkeEtInnkommendeDokument -> KunneIkkeHenteJournalpost.JournalpostenErIkkeEtInnkommendeDokument
                        JournalpostErIkkeFerdigstilt.JournalpostenErIkkeFerdigstilt -> KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt
                    }
                }
        }
    }

    override fun kontrollnotatMotatt(saksnummer: Saksnummer, periode: Periode): Either<KunneIkkeSjekkKontrollnotatMottatt, Boolean> {
        val request = GraphQLQuery<HentDokumentoversiktFagsakHttpResponse>(
            query = lagRequest(
                query = "/dokumentoversiktFagsakQuery.graphql",
                datafelter = "/hentMottattKontrollnotatDatafelter.graphql"
            ),
            variables = HentJournalpostForFagsakVariables(
                fagsakId = saksnummer.toString(),
                fagsaksystem = "SUPSTONAD",
                fraDato = periode.fraOgMed.toString(),
                tema = "SUP",
                journalposttyper = listOf("I"),
                journalstatuser = listOf("JOURNALFOERT"),
                foerste = 100
            )
        )
        return gqlRequest(
            request = request,
            token = sts.token().value,
        ).mapLeft { error ->
            KunneIkkeSjekkKontrollnotatMottatt(error).also { log.error("Feil: $it ved henting av journalposter for saksnummer:$saksnummer") }
        }.map { response ->
            response.data!!.dokumentoversiktFagsak.journalposter
                .toDomain()
                .any { periode.inneholder(it.datoOpprettet) && it.tittel.contains("NAV SU Kontrollnotat") }
        }
    }

    private fun lagRequest(
        query: String,
        datafelter: String,
    ): String {
        val gqlQuery = javaClass.getResource(query)?.readText() ?: throw IllegalArgumentException("Fant ikke fil med navn: $query")
        val data = javaClass.getResource(datafelter)?.readText() ?: throw IllegalArgumentException("Fant ikke fil med navn: $query")
        return gqlQuery.replace("<<DATAFELTER>>", data)
    }

    private inline fun <reified Response : GraphQLHttpResponse> gqlRequest(request: GraphQLQuery<Response>, token: String): Either<GraphQLApiFeil, Response> {
        return Either.catch {
            HttpRequest.newBuilder(graphQLUrl)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Nav-Consumer-Id", "su-se-bakover")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build()
                .let { httpRequest ->
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
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
                                return GraphQLApiFeil.HttpFeil.Ukjent(request, """Status: ${httpResponse.statusCode()}, Body:${httpResponse.body()}""")
                                    .also { log.warn("Feil: $it ved kall mot: $graphQLUrl") }
                                    .left()
                            }
                        }
                }
        }.mapLeft { throwable ->
            GraphQLApiFeil.TekniskFeil(request, throwable.toString())
                .also { log.warn("Feil: $it ved kall mot: $graphQLUrl") }
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
    val variables: Any
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
