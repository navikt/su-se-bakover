package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnhold
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.*

internal const val dokArkivPath = "/rest/journalpostapi/v1/journalpost"
private val log = LoggerFactory.getLogger(DokArkivClient::class.java)

internal class DokArkivClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag
) : DokArkiv {
    override fun opprettJournalpost(
        søknadInnhold: SøknadInnhold,
        person: Person,
        pdf: ByteArray,
        sakId: String
    ): Either<ClientError, String> {
        val (_, response, result) = "$baseUrl$dokArkivPath".httpPost(listOf("forsoekFerdigstill" to "true"))
            .authentication().bearer(tokenOppslag.token())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                byggJournalpostJson(person.ident.fnr, søkersNavn(person), søknadInnhold, sakId, pdf)
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

    fun byggJournalpostJson(fnr: Fnr, navn: String, søknadInnhold: SøknadInnhold, sakId: String, pdf: ByteArray): String {
        return """
                    {
                      "tittel": "Søknad om supplerende stønad for uføre flyktninger",
                      "journalpostType": "INNGAAENDE",
                      "tema": "SUP",
                      "kanal": "INNSENDT_NAV_ANSATT",
                      "behandlingstema": "ab0268",
                      "journalfoerendeEnhet": "9999",
                      "avsenderMottaker": {
                        "id": "$fnr",
                        "idType": "FNR",
                        "navn": "$navn"
                      },
                      "bruker": {
                        "id": "$fnr",
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
                          "dokumentKategori": "SOK",
                          "brevkode": "XX.YY-ZZ",
                          "dokumentvarianter": [
                            {
                              "filtype": "PDFA",
                              "fysiskDokument": "${Base64.getEncoder().encodeToString(pdf)}",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filtype": "JSON",
                              "fysiskDokument": "${Base64.getEncoder()
            .encodeToString(objectMapper.writeValueAsString(søknadInnhold).toByteArray())}",
                              "variantformat": "ORIGINAL"
                            }
                          ]
                        }
                      ]
                    }
        """.trimIndent()
    }

    private fun søkersNavn(person: Person): String =
        """${person.navn.etternavn}, ${person.navn.fornavn} ${person.navn.mellomnavn ?: ""}""".trimEnd()
}
