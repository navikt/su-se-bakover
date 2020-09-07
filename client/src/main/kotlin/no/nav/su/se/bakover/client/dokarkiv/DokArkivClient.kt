package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal const val dokArkivPath = "/rest/journalpostapi/v1/journalpost"
private val log = LoggerFactory.getLogger(DokArkivClient::class.java)

class DokArkivClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag
) : DokArkiv {
    override fun opprettJournalpost(
        dokumentInnhold: Journalpost,
    ): Either<ClientError, String> {
        val (_, response, result) = "$baseUrl$dokArkivPath".httpPost(listOf("forsoekFerdigstill" to "true"))
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                objectMapper.writeValueAsString(
                    JournalpostRequest(
                        tittel = dokumentInnhold.tittel,
                        journalpostType = dokumentInnhold.journalpostType,
                        tema = dokumentInnhold.tema,
                        kanal = dokumentInnhold.kanal,
                        behandlingstema = dokumentInnhold.behandlingstema,
                        journalfoerendeEnhet = dokumentInnhold.journalfoerendeEnhet,
                        avsenderMottaker = dokumentInnhold.avsenderMottaker,
                        bruker = dokumentInnhold.bruker,
                        sak = dokumentInnhold.sak,
                        dokumenter = dokumentInnhold.dokumenter
                    )
                )
            ).responseString()

        return result.fold(
            { json ->
                JSONObject(json).let {
                    val journalpostId: String? = it.optString("journalpostId", null)

                    if (!it.optBoolean("journalpostferdigstilt", false)) {
                        log.warn("Kunne ikke ferdigstille journalføring for journalpostId: $journalpostId. body=$json")
                    }

                    journalpostId?.right() ?: ClientError(response.statusCode, "Feil ved journalføring av søknad.").left().also {
                        log.warn("Kunne ikke ferdigstille journalføring, fant ingen journalpostId. body=$json")
                    }
                }
            },
            {
                log.warn("Feil ved journalføring av søknad.", it)
                ClientError(response.statusCode, "Feil ved journalføring av søknad.").left()
            }

        )
    }
}
