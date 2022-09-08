package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// docs: https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning#

private data class JournalpostRequest(
    val query: String,
    val variables: JournalpostVariables,
)

private data class JournalpostVariables(
    val journalpostId: String,
)

internal class JournalpostHttpClient(
    private val safConfig: ApplicationConfig.ClientsConfig.SafConfig,
    private val azureAd: AzureAd,
) : JournalpostClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    var client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun hentFerdigstiltJournalpost(
        saksnummer: Saksnummer,
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, FerdigstiltJournalpost> {
        val request = JournalpostRequest(
            this::class.java.getResource("/hentJournalpost.graphql")?.readText()!!,
            JournalpostVariables(journalpostId.toString()),
        )
        return hentJournalpostFraSaf(request).mapLeft {
            it
        }.flatMap {
            it.toValidertInnkommendeJournalførtJournalpost(saksnummer)
        }
    }

    private fun hentJournalpostFraSaf(request: JournalpostRequest): Either<KunneIkkeHenteJournalpost, JournalpostResponse> {
        val token = "Bearer ${azureAd.onBehalfOfToken(MDC.get("Authorization"), safConfig.clientId)}"

        return Either.catch {
            val req = HttpRequest.newBuilder(URI.create("${safConfig.url}/graphql"))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .header("Nav-Consumer-Id", "su-se-bakover")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(request),
                    ),
                ).build()

            client.send(req, HttpResponse.BodyHandlers.ofString()).let {
                val body = it.body()
                if (it.isSuccess()) {
                    Either.catch {
                        val journalpostHttpResponse = objectMapper.readValue<JournalpostHttpResponse>(body)
                        if (journalpostHttpResponse.hasErrors()) {
                            return journalpostHttpResponse.tilKunneIkkeHenteJournalpost(request.variables.journalpostId)
                                .left()
                        }
                        return journalpostHttpResponse.data.right()
                    }.mapLeft { exception ->
                        log.error("Feil i kallet mot SAF. status: ${it.statusCode()} for journalpostId: ${request.variables.journalpostId}. Se sikker logg for innholdet av body, og exception")
                        sikkerLogg.info(
                            "Feil i kallet mot SAF for journalpostId: ${request.variables.journalpostId}. status: ${it.statusCode()}, body: $body",
                            exception,
                        )
                        return KunneIkkeHenteJournalpost.TekniskFeil.left()
                    }
                }

                log.error("Feil i kallet mot SAF. status: ${it.statusCode()} for journalpostId: ${request.variables.journalpostId}. Se sikker logg for innholdet av body")
                sikkerLogg.info("Feil i kallet mot SAF. status: ${it.statusCode()}, body: $body for journalpostId: ${request.variables.journalpostId}")
                KunneIkkeHenteJournalpost.Ukjent.left()
            }
        }.mapLeft {
            log.error("Feil i kallet mot SAF.", it)
            KunneIkkeHenteJournalpost.Ukjent
        }.flatten()
    }
}

// https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning#safUtviklerveiledning-Feilh%C3%A5ndtering
internal data class JournalpostHttpResponse(
    val data: JournalpostResponse,
    val errors: List<Error>?,
) {
    fun hasErrors(): Boolean {
        return errors !== null && errors.isNotEmpty()
    }

    fun tilKunneIkkeHenteJournalpost(journalpostId: String): KunneIkkeHenteJournalpost {
        return errors.orEmpty().map { error ->
            when (error.extensions.code) {
                "forbidden" -> KunneIkkeHenteJournalpost.IkkeTilgang.also {
                    log.error("Ikke tilgang til Journalpost. Id $journalpostId")
                }
                "not_found" -> KunneIkkeHenteJournalpost.FantIkkeJournalpost.also {
                    log.info("Fant ikke journalpost. Id $journalpostId")
                }
                "bad_request" -> KunneIkkeHenteJournalpost.UgyldigInput.also {
                    log.error("Sendt ugyldig input til SAF. Id $journalpostId")
                }
                "server_error" -> KunneIkkeHenteJournalpost.TekniskFeil.also {
                    log.error("Teknisk feil hos SAF. Id $journalpostId")
                }
                else -> KunneIkkeHenteJournalpost.Ukjent.also {
                    log.error("Uhåndtert feil fra SAF. code ${error.extensions.code}. Id $journalpostId")
                }
            }
        }.first()
    }
}

internal data class Error(
    val message: String,
    val path: List<String>,
    val extensions: Extensions,
)

internal data class Extensions(
    val code: String,
    val classification: String,
)
