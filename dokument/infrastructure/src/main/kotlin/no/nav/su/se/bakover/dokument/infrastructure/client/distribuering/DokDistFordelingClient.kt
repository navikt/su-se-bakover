package no.nav.su.se.bakover.dokument.infrastructure.client.distribuering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.distribuering.KunneIkkeBestilleDistribusjon
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.sikkerLogg
import org.json.JSONObject
import org.slf4j.LoggerFactory

internal const val DOK_DIST_FORDELING_PATH = "/rest/v1/distribuerjournalpost"

/**
 * https://confluence.adeo.no/pages/viewpage.action?pageId=320039012
 */

enum class FEILKODER {
    BRUKER_ER_DØD,
    FEILREGISTRERT,
}
class DokDistFordelingClient(
    private val dokDistConfig: ApplicationConfig.ClientsConfig.DokDistConfig,
    private val azureAd: AzureAd,
) : DokDistFordeling {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun bestillDistribusjon(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
        distribueringsadresse: Distribueringsadresse?,
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId> {
        val requestBody = toDokDistRequestJson(
            journalpostId = journalpostId,
            distribusjonstype = distribusjonstype,
            distribusjonstidspunkt = distribusjonstidspunkt,
            distribueringsadresse = distribueringsadresse,
        )
        val (_, _, result) = "${dokDistConfig.url}$DOK_DIST_FORDELING_PATH".httpPost()
            .authentication().bearer(azureAd.getSystemToken(dokDistConfig.clientId))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Nav-CallId", getOrCreateCorrelationIdFromThreadLocal())
            .body(requestBody).responseString()

        return result.fold(
            { hentBrevbestillingsId(it, journalpostId).right() },
            {
                val response = it.response
                // 409 conflict. journalposten har allerede fått bestilt distribusjon -
                if (response.statusCode == 409) {
                    return hentBrevbestillingsId(String(response.data), journalpostId).right()
                } else if (response.statusCode == HttpStatusCode.Gone.value) {
                    val body = String(response.data)
                    if (body.lowercase().contains("Mottaker er død og har ukjent adresse.".lowercase())) {
                        log.error("Mottaker er død")
                        sikkerLogg.error("body=$body")
                        return BrevbestillingId(FEILKODER.BRUKER_ER_DØD.name).right()
                    } else {
                        log.error("Mottaker er død, feil body msg.. se sikkerlogg")
                        sikkerLogg.error("body=$body")
                        return KunneIkkeBestilleDistribusjon.left()
                    }
                } else {
                    val body = String(response.data)
                    if (response.statusCode == HttpStatusCode.BadRequest.value) {
                        /*
                           Dette skjer når saksbehandler ikke vil ha distribusjon av et brev pga en feil, da går de inn i gosys og trykker feilregistrert på journalposten før brevet har blitt sendt.
                           For å unngå evig loop på retry setter vi iden til feilregistrert.
                         */
                        if (body.lowercase().contains("Validering av distribusjonsforespørsel feilet med feilmelding: Journalpostfeltet journalpoststatus er ikke som forventet, fikk: FEILREGISTRERT, men forventet FERDIGSTILT".lowercase())) {
                            return BrevbestillingId(FEILKODER.FEILREGISTRERT.name).right()
                        } else {
                            log.error("Feilkode matcher ikke lenger, må sjekke dette")
                        }
                    }
                    log.error(
                        "Feil ved bestilling av distribusjon. status=${response.statusCode} body=$body",
                        it,
                    )
                    return KunneIkkeBestilleDistribusjon.left()
                }
            },
        )
    }

    private fun hentBrevbestillingsId(
        json: String,
        journalPostId: JournalpostId,
    ): BrevbestillingId {
        val brevbestillingsId: String? = JSONObject(json).optString("bestillingsId", null)
        if (brevbestillingsId == null) {
            log.error("Bestilt distribusjon, men bestillingsId manglet i responsen for journalpost $journalPostId. Denne må fikses manuelt")
        } else {
            log.info("Bestilt distribusjon med bestillingsId $brevbestillingsId")
        }
        return BrevbestillingId(brevbestillingsId ?: "ikke_mottatt_fra_ekstern_tjeneste")
    }
}
