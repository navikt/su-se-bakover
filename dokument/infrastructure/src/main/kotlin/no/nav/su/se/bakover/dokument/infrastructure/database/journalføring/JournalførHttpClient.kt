package no.nav.su.se.bakover.dokument.infrastructure.database.journalføring

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.json.JSONObject
import org.slf4j.LoggerFactory

const val DOK_ARKIV_PATH = "/rest/journalpostapi/v1/journalpost"

// https://confluence.adeo.no/display/BOA/opprettJournalpost
// swagger: https://dokarkiv-q2.dev.intern.nav.no/swagger-ui/index.html#/
class JournalførHttpClient(
    private val dokArkivConfig: ApplicationConfig.ClientsConfig.DokArkivConfig,
    private val azureAd: AzureAd,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun opprettJournalpost(
        jsonDto: JournalførJsonRequest,
    ): Either<ClientError, JournalpostId> {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()

        val (request, response, result) = "${dokArkivConfig.url}$DOK_ARKIV_PATH".httpPost(listOf("forsoekFerdigstill" to "true"))
            .authentication().bearer(azureAd.getSystemToken(dokArkivConfig.clientId))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header(CORRELATION_ID_HEADER, correlationId)
            .body(serialize(jsonDto)).responseString()

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
                val status = response.statusCode
                val body = String(response.data)
                if (status == 409) {
                    hentJournalpostIdVedConflict(body, request)
                } else {
                    log.error(
                        "Feil ved journalføring. status=$status body=$body. Se sikker logg for mer detaljer",
                        it,
                    )
                    sikkerLogg.error(
                        "Feil ved journalføring " +
                            "Request $request er forespørselen mot dokarkiv som feilet. Headere ${request.headers}",
                    )

                    ClientError(response.statusCode, "Feil ved journalføring").left()
                }
            },
        )
    }

    private fun hentJournalpostIdVedConflict(body: String, request: Request): Either<ClientError, JournalpostId> {
        val json = JSONObject(body)
        val journalpostId = json.optString("journalpostId", null) ?: run {
            log.error(
                "Kunne ikke hente journalpostId ved 409 Conflict. Se sikkerlogg for mer kontekst",
                RuntimeException("Trigger stacktrace."),
            )
            sikkerLogg.error(
                "Kunne ikke hente journalpostId ved 409 Conflict. body=$body. Request $request er forespørselen mot dokarkiv som feilet. Headere ${request.headers}",
            )
            return ClientError(409, "Feil ved journalføring").left()
        }
        json.optBooleanObject("journalpostferdigstilt", null)?.let {
            if (!it) {
                log.error(
                    "Journalpost var ikke ferdigstilt ved 409 Conflict. journalpostId=$journalpostId. Se sikklerlogg for mer kontekst",
                    RuntimeException("Trigger stacktrace."),
                )
                sikkerLogg.error(
                    "Journalpost var ikke ferdigstilt ved 409 Conflict. journalpostId=$journalpostId. body=$json. Request $request er forespørselen mot dokarkiv som feilet. Headere ${request.headers}",
                )
                return ClientError(409, "Feil ved journalføring").left()
            }
        } ?: run {
            log.error(
                "Kunne ikke hente journalpostferdigstilt ved 409 Conflict. journalpostId=$journalpostId. Se sikkerlogg for mer kontekst",
                RuntimeException("Trigger stacktrace."),
            )
            sikkerLogg.error(
                "Kunne ikke hente journalpostferdigstilt ved 409 Conflict. journalpostId=$journalpostId. body=$body. Request $request er forespørselen mot dokarkiv som feilet. Headere ${request.headers}",
            )
            return ClientError(409, "Feil ved journalføring").left()
        }
        return JournalpostId(journalpostId).right()
    }
}
