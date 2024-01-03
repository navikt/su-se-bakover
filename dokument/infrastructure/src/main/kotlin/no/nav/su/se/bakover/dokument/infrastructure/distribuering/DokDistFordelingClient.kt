package no.nav.su.se.bakover.dokument.infrastructure.distribuering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.distribuering.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.common.domain.auth.TokenOppslag
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.journal.JournalpostId
import org.json.JSONObject
import org.slf4j.LoggerFactory

internal const val DOK_DIST_FORDELING_PATH = "/rest/v1/distribuerjournalpost"

/**
 * https://confluence.adeo.no/pages/viewpage.action?pageId=320039012
 */
class DokDistFordelingClient(val baseUrl: String, val tokenOppslag: TokenOppslag) : DokDistFordeling {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun bestillDistribusjon(
        journalPostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId> {
        val body = byggDistribusjonPostJson(journalPostId, distribusjonstype, distribusjonstidspunkt)
        val (_, _, result) = "$baseUrl$DOK_DIST_FORDELING_PATH".httpPost()
            .authentication().bearer(tokenOppslag.token().value)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Nav-CallId", getOrCreateCorrelationIdFromThreadLocal())
            .body(body).responseString()

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
                val response = it.response

                // 409 conflict. journalposten har allerede fått bestilt distribusjon -
                if (response.statusCode == 409) {
                    val bestillingsId = JSONObject(String(response.data)).optString("bestillingsId")
                    log.info("Journalpost $journalPostId har allerede fått bestilt distribusjon med id $bestillingsId", it)
                    BrevbestillingId(bestillingsId).right()
                } else {
                    log.error(
                        "Feil ved bestilling av distribusjon. status=${response.statusCode} body=${String(response.data)}",
                        it,
                    )
                    KunneIkkeBestilleDistribusjon.left()
                }
            },
        )
    }

    internal fun byggDistribusjonPostJson(
        journalPostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
    ): String {
        return """
                    {
                        "journalpostId": "$journalPostId",
                        "bestillendeFagsystem": "SUPSTONAD",
                        "dokumentProdApp": "SU_SE_BAKOVER",
                        "distribusjonstype": "${distribusjonstype.toDokdistFordelingType()}",
                        "distribusjonstidspunkt": "${distribusjonstidspunkt.toDokdistFordelingType()}"
                    }
        """.trimIndent()
    }

    private fun Distribusjonstype.toDokdistFordelingType() = when (this) {
        Distribusjonstype.VEDTAK -> PayloadTyper.Distribusjonstype.VEDTAK.value
        Distribusjonstype.VIKTIG -> PayloadTyper.Distribusjonstype.VIKTIG.value
        Distribusjonstype.ANNET -> PayloadTyper.Distribusjonstype.ANNET.value
    }

    private fun Distribusjonstidspunkt.toDokdistFordelingType() = when (this) {
        Distribusjonstidspunkt.UMIDDELBART -> PayloadTyper.Distribusjonstidspunkt.UMIDDELBART.value
        Distribusjonstidspunkt.KJERNETID -> PayloadTyper.Distribusjonstidspunkt.KJERNETID.value
    }
}

private sealed interface PayloadTyper {
    enum class Distribusjonstype(val value: String) {
        VEDTAK("VEDTAK"),
        VIKTIG("VIKTIG"),
        ANNET("ANNET"),
    }

    enum class Distribusjonstidspunkt(val value: String) {
        UMIDDELBART("UMIDDELBART"),
        KJERNETID("KJERNETID"),
    }
}
