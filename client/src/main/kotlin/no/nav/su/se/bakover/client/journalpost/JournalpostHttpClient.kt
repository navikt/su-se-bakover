package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private data class JournalpostRequest(
    val query: String,
    val variables: JournalpostVariables,
)

private data class JournalpostVariables(
    val journalpostId: String,
)

internal class JournalpostHttpClient(
    val safUrl: String,
    val tokenOppslag: TokenOppslag,
) : JournalpostClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val query = this::class.java.getResource("/hentJournalpost.graphql")?.readText()!!

    override fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, HentetJournalpost> {
        val request = JournalpostRequest(query, JournalpostVariables(journalpostId.toString()))
        return hentJournalpostFraSaf(request).mapLeft {
            it
        }.flatMap { journalpostResponse ->
            journalpostResponse.toHentetJournalpost().mapLeft {
                KunneIkkeHenteJournalpost.FantIkkeJournalpost
            }.map {
                it
            }
        }
    }

    private fun hentJournalpostFraSaf(request: JournalpostRequest): Either<KunneIkkeHenteJournalpost, JournalpostResponse> {
        val token = "Bearer ${tokenOppslag.token()}"
        val (_, response, result) = safUrl.httpPost()
            .header("Authorization", token)
            .header("Content-Type", "application/json")
            .header("Nav-Consumer-Id", "su-se-bakover")
            .body(objectMapper.writeValueAsString(request))
            .responseString()

        return result.fold(
            {
                val JournalpostHttpResponse = objectMapper.readValue<JournalpostHttpResponse>(it)
                if (JournalpostHttpResponse.hasErrors()) {
                    return JournalpostHttpResponse.tilKunneIkkeHenteJournalpost().left()
                }
                return JournalpostHttpResponse.data.right()
            },
            {
                val statusCode = response.statusCode
                val message = response.responseMessage
                log.error("Feil i kallet mot SAF. status: $statusCode, message: $message for journalpostId: ${request.variables.journalpostId}")
                KunneIkkeHenteJournalpost.Ukjent.left()
            },
        )
    }
}

internal data class JournalpostHttpResponse(
    val data: JournalpostResponse,
    val errors: List<Error>?,
) {
    fun hasErrors(): Boolean {
        return errors !== null && errors.isNotEmpty()
    }

    fun tilKunneIkkeHenteJournalpost(): KunneIkkeHenteJournalpost {
        return errors.orEmpty().map { error ->
            when (error.extensions.code) {
                "forbidden" -> KunneIkkeHenteJournalpost.IkkeTilgang
                "not_found" -> KunneIkkeHenteJournalpost.FantIkkeJournalpost
                "bad_request" -> KunneIkkeHenteJournalpost.UgyldigInput
                "server_error" -> KunneIkkeHenteJournalpost.TekniskFeil
                else -> KunneIkkeHenteJournalpost.Ukjent.also {
                    log.warn("Uhåndtert feil fra SAF. code ${error.extensions.code} ")
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
