package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.CorrelationIdHeader
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.journalpost.JournalpostCommand
import org.json.JSONObject
import org.slf4j.LoggerFactory

internal const val dokArkivPath = "/rest/journalpostapi/v1/journalpost"

// https://confluence.adeo.no/display/BOA/opprettJournalpost
// swagger: https://dokarkiv-q2.nais.preprod.local/swagger-ui.html#/arkiver-og-journalfoer-rest-controller/opprettJournalpostUsingPOST
class DokArkivClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag,
) : DokArkiv {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun opprettJournalpost(
        dokumentInnhold: JournalpostCommand,
    ): Either<ClientError, JournalpostId> {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()

        val (request, response, result) = "$baseUrl$dokArkivPath".httpPost(listOf("forsoekFerdigstill" to "true"))
            .authentication().bearer(tokenOppslag.token().value)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Nav-Callid", correlationId.toString())
            .header(CorrelationIdHeader, correlationId)
            .body(dokumentInnhold.tilJson()).responseString()

        return result.fold(
            { json ->
                JSONObject(json).let {
                    val journalpostId: String? = it.optString("journalpostId", null)

                    if (!it.optBoolean("journalpostferdigstilt", false)) {
                        log.error("Kunne ikke ferdigstille journalføring for journalpostId: $journalpostId. body=$json")
                    }

                    if (journalpostId != null) {
                        log.info("Opprettet journalpost med id $journalpostId")
                        JournalpostId(journalpostId).right()
                    } else {
                        log.error("Kunne ikke ferdigstille journalføring, fant ingen journalpostId. body=$json")
                        ClientError(response.statusCode, "Feil ved journalføring.").left()
                    }
                }
            },
            {
                log.error("Feil ved journalføring. status=${response.statusCode} body=${String(response.data)}. Se sikker logg for mer detaljer", it)
                sikkerLogg.error(
                    "Feil ved journalføring " +
                        "Request $request er forespørselen mot dokarkiv som feilet. Headere ${request.headers}",
                )

                ClientError(response.statusCode, "Feil ved journalføring").left()
            },
        )
    }
}
