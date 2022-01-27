package no.nav.su.se.bakover.client.saf

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal data class JournalpostRequest(
    val query: String,
    val journalpostId: JournalpostId,
)

internal class JournalpostHttpClient(
    val config: ApplicationConfig.ClientsConfig,
    val tokenOppslag: TokenOppslag,
) : JournalpostClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val query = this::class.java.getResource("/hentJournalpost.graphql")?.readText()!!

    override fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, HentetJournalpost> {
        val request = JournalpostRequest(query, journalpostId)
        return hentJournalpostFraSaf(request).mapLeft {
            it
        }.map {
            it.toHentetJournalpost()
        }
    }

    private fun hentJournalpostFraSaf(request: JournalpostRequest): Either<KunneIkkeHenteJournalpost, SafResponse> {
        val token = "Bearer ${tokenOppslag.token()}"
        val (_, response, result) = config.safUrl.httpPost()
            .header("Authorization", token)
            .header("Content-Type", "application/json")
            .header("Nav-Consumer-Id", "su-se-bakover")
            .body(objectMapper.writeValueAsString(request))
            .responseString()

        return result.fold(
            {
                val safHttpResponse = objectMapper.readValue(it, SafHttpResponse::class.java)
                if (safHttpResponse.hasErrors()) {
                    return safHttpResponse.tilKunneIkkeHenteJournalpost().left()
                }
                return safHttpResponse.data.right()
            },
            {
                val statusCode = response.statusCode
                val message = response.responseMessage
                log.error("Feil i kallet mot SAF. status: $statusCode, message: $message for journalpostId: ${request.journalpostId}")
                KunneIkkeHenteJournalpost.Ukjent.left()
            },
        )
    }
}

internal data class SafHttpResponse(
    val data: SafResponse,
    val errors: List<Error>?,
) {
    fun hasErrors(): Boolean {
        return errors !== null && errors.isNotEmpty()
    }

    fun tilKunneIkkeHenteJournalpost(): KunneIkkeHenteJournalpost {
        return errors.orEmpty().map {
            when (it.extensions.code) {
                "not_found" -> KunneIkkeHenteJournalpost.FantIkkeJournalpost
                "forbidden" -> KunneIkkeHenteJournalpost.IkkeTilgang
                "Unauthorized" -> KunneIkkeHenteJournalpost.IkkeTilgang
                "server_error" -> KunneIkkeHenteJournalpost.TekniskFeil
                else -> throw IllegalStateException("Uhåndtert feil fra SAF. code ${it.extensions.code} ")
            }
        }.first()
    }
}

internal data class Error(val message: String, val path: List<String>, val extensions: Extensions)

internal data class Extensions(val code: String, val classification: String)
