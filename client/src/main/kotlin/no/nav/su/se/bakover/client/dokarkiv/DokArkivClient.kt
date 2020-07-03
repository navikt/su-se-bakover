package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.meldinger.kafka.soknad.Personopplysninger
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.CallContext
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.Base64

internal val dokArkivPath = "/rest/journalpostapi/v1/journalpost"
private val log = LoggerFactory.getLogger(DokArkivClient::class.java)

internal class DokArkivClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag
) : DokArkiv {
    override fun opprettJournalpost(
        søknadInnhold: SøknadInnhold,
        pdf: ByteArray,
        sakId: Long
    ): Either<ClientError, String> {
        val (_, response, result) = "$baseUrl$dokArkivPath".httpPost(listOf("forsoekFerdigstill" to "true"))
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", CallContext.correlationId())
            .body(
                """
                    {
                      "tittel": "Søknad om supplerende stønad for uføre flyktninger",
                      "journalpostType": "INNGAAENDE",
                      "tema": "SUP",
                      "kanal": "NAV_NO",
                      "behandlingstema": "ab0268",
                      "journalfoerendeEnhet": "9999",
                      "avsenderMottaker": {
                        "id": "${søknadInnhold.personopplysninger.fnr}",
                        "idType": "FNR",
                        "navn": "${søkersNavn(søknadInnhold.personopplysninger)}"
                      },
                      "bruker": {
                        "id": "${søknadInnhold.personopplysninger.fnr}",
                        "idType": "FNR"
                      },
                      "sak": {
                        "fagsakId": "$sakId",
                        "fagsaksystem": "SUPSTONAD",
                        "sakstype": "FAGSAK"
                      },
                      "dokumenter": [
                        {
                          "tittel": "Søknad om supplerende stønad for uføre flyktninger",
                          "dokumentvarianter": [
                            {
                              "filtype": "PDFA",
                              "fysiskDokument": "${Base64.getEncoder().encodeToString(pdf)}",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filtype": "JSON",
                              "fysiskDokument": "${Base64.getEncoder()
                    .encodeToString(søknadInnhold.toJson().toByteArray())}",
                              "variantformat": "ORIGINAL"
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent()
            ).responseString()

        return result.fold(
            { json ->
                JSONObject(json).let {
                    val journalpostId = it.getString("journalpostId")

                    if (it.getBoolean("journalpostferdigstilt")) {
                        log.warn("Kunne ikke ferdigstille journalføring for journalpostId: $journalpostId")
                    }
                    journalpostId.right()
                }
            },
            {
                log.warn("Feil ved journalføring av søknad.", it)
                ClientError(response.statusCode, "Feil ved journalføring av søknad.").left()
            }

        )
    }

    private fun søkersNavn(personopplysninger: Personopplysninger): String =
        """${personopplysninger.etternavn}, ${personopplysninger.fornavn} ${personopplysninger.mellomnavn ?: ""}"""
}
