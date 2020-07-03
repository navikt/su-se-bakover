package no.nav.su.se.bakover.client.dokarkiv

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.Personopplysninger
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.client.sts.TokenOppslag
import org.json.JSONObject
import java.util.Base64

val dokArkivPath = "/rest/journalpostapi/v1/journalpost"

internal class DokArkivClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag
) : DokArkiv {
    override fun opprettJournalpost(nySøknad: NySøknad, pdf: ByteArray): String {
        val søknadInnhold = SøknadInnhold.fromJson(JSONObject(nySøknad.søknad))
        val (_, _, result) = "$baseUrl$dokArkivPath".httpPost(listOf("forsoekFerdigstill" to "true"))
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", nySøknad.correlationId)
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
                        "id": "${nySøknad.fnr}",
                        "idType": "FNR",
                        "navn": "${søkersNavn(søknadInnhold.personopplysninger)}"
                      },
                      "bruker": {
                        "id": "${nySøknad.fnr}",
                        "idType": "FNR"
                      },
                      "sak": {
                        "fagsakId": "${nySøknad.sakId}",
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
                              "fysiskDokument": "${Base64.getEncoder().encodeToString(nySøknad.søknad.toByteArray())}",
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
                    when (it.getBoolean("journalpostferdigstilt")) {
                        true -> it.getString("journalpostId")
                        else -> throw RuntimeException("Kunne ikke ferdigstille journalføring")
                    }
                }
            },
            { error ->
                val statusCode = error.response.statusCode
                throw RuntimeException("Feil ved journalføring av søknad. statusCode=$statusCode", error)
            }
        )
    }

    private fun søkersNavn(personopplysninger: Personopplysninger): String =
        """${personopplysninger.etternavn}, ${personopplysninger.fornavn} ${personopplysninger.mellomnavn ?: ""}"""
}
