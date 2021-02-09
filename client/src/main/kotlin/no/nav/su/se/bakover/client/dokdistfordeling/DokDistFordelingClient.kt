package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.json.JSONObject
import org.slf4j.LoggerFactory

internal const val dokDistFordelingPath = "/rest/v1/distribuerjournalpost"
class DokDistFordelingClient(val baseUrl: String, val tokenOppslag: TokenOppslag) : DokDistFordeling {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun bestillDistribusjon(
        journalPostId: JournalpostId
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId> {
        val body = byggDistribusjonPostJson(journalPostId)
        val (_, _, result) = "$baseUrl$dokDistFordelingPath".httpPost()
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", getOrCreateCorrelationId())
            .body(
                body
            ).responseString()

        return result.fold(
            { json ->
                JSONObject(json).let {
                    val eksternId: String? = it.optString("bestillingsId", null)

                    if (eksternId == null) {
                        log.error("Bestilt distribusjon, men bestillingsId manglet i responsen. Dette må følges opp manuelt.")
                    } else {
                        log.info("Bestilt distribusjon med bestillingsId $eksternId")
                    }
                    BrevbestillingId(eksternId ?: "ikke_mottatt_fra_ekstern_tjeneste").right()
                }
            },
            {
                log.error("Feil ved bestilling av distribusjon.", it)
                KunneIkkeBestilleDistribusjon.left()
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
