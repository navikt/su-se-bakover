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
import no.nav.su.se.bakover.common.objectMapper
import org.json.JSONObject
import org.slf4j.LoggerFactory

internal const val dokArkivPath = "/rest/journalpostapi/v1/journalpost"

//https://confluence.adeo.no/display/BOA/opprettJournalpost
//swagger: https://dokarkiv-q2.nais.preprod.local/swagger-ui.html#/arkiver-og-journalfoer-rest-controller/opprettJournalpostUsingPOST
class DokArkivClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag,
) : DokArkiv {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun opprettJournalpost(
        dokumentInnhold: Journalpost,
    ): Either<ClientError, JournalpostId> {
        val (_, response, result) = "$baseUrl$dokArkivPath".httpPost(listOf("forsoekFerdigstill" to "true"))
            .authentication().bearer(tokenOppslag.token().value)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header(CorrelationIdHeader, getOrCreateCorrelationIdFromThreadLocal())
            .body(
                objectMapper.writeValueAsString(
                    JournalpostRequest(
                        tittel = dokumentInnhold.tittel,
                        journalpostType = dokumentInnhold.journalpostType,
                        tema = dokumentInnhold.tema,
                        kanal = dokumentInnhold.kanal,
                        behandlingstema = dokumentInnhold.behandlingstema,
                        journalfoerendeEnhet = dokumentInnhold.journalfoerendeEnhet.enhet,
                        avsenderMottaker = dokumentInnhold.avsenderMottaker,
                        bruker = dokumentInnhold.bruker,
                        sak = dokumentInnhold.sak,
                        dokumenter = dokumentInnhold.dokumenter,
                    ),
                ),
            ).responseString()

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
                log.error("Feil ved journalføring. status=${response.statusCode} body=${String(response.data)}", it)
                ClientError(response.statusCode, "Feil ved journalføring").left()
            },

        )
    }
}
