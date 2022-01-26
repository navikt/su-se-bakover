package no.nav.su.se.bakover.client.saf

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class SafHttpClient(
    val config: ApplicationConfig.ClientsConfig,
    val tokenOppslag: TokenOppslag,
) : SafClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val query = this::class.java.getResource("/hentJournalpost.graphql")?.readText()!!

    override fun hentJournalpost(journalpostId: JournalpostId): Either<KunneIkkeHenteJournalpost, Journalpost> {
        val request = SafRequest(query, journalpostId.toString())
        return hentJournalpostFraSaf(request).mapLeft {
            it
        }.map {
            TODO()
        }
    }

    private fun hentJournalpostFraSaf(request: SafRequest): Either<KunneIkkeHenteJournalpost, SafResponse> {
        val token = "Bearer ${tokenOppslag.token()}"
        val (_, response, result) = config.safUrl.httpPost()
            .header("Authorization", token)
            .header("Content-Type", "application/json")
            .header("Nav-Consumer-Id", "su-se-bakover")
            .body(objectMapper.writeValueAsString(request))
            .responseString()

        return result.fold(
            {
                objectMapper.readValue(it, SafResponse::class.java).right()
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

data class SafRequest(
    val query: String,
    val journalpostId: String,
)
