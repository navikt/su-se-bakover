package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal const val dokDistFordelingPath = "/rest/v1/distribuerjournalpost"
class DokDistFordelingClient(val baseUrl: String, val tokenOppslag: TokenOppslag) : DokDistFordeling {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun bestillDistribusjon(
        journalPostId: JournalpostId
    ): Either<ClientError, String> {
        val body = byggDistribusjonPostJson(journalPostId)
        val (_, response, result) = "$baseUrl$dokDistFordelingPath".httpPost()
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                body
            ).responseString()

        return result.fold(
            {
                json ->
                JSONObject(json).let {
                    val bestillingsId = it.optString("bestillingsId")
                    log.info("Bestilt distribusjon med bestillingsId $bestillingsId")
                    bestillingsId.right()
                }
            },
            {
                log.error("Feil ved bestilling av distribusjon.", it)
                ClientError(response.statusCode, "Feil ved bestilling av distribusjon.").left()
            }
        )
    }

    fun byggDistribusjonPostJson(journalPostId: JournalpostId): String {
        return """
                    {
                        "journalpostId": "$journalPostId",
                        "bestillendeFagsystem": "SUPSTONAD",
                        "dokumentProdApp": "SU_SE_BAKOVER"
                    }
        """.trimIndent()
    }
}
