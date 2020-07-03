package no.nav.su.se.bakover.client.oppgave

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.person.sts.TokenOppslag
import org.json.JSONObject
import java.time.LocalDate

internal val oppgavePath = "/api/v1/oppgaver"

private const val TEMA_SU_UFØR_FLYKTNING = "ab0431"
private const val TYPE_FØRSTEGANGSSØKNAD = "ae0245"

interface Oppgave {
    fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Long
}

internal class OppgaveClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag
) : Oppgave {
    override fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Long {
        val (_, _, result) = "$baseUrl$oppgavePath".httpPost()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", nySøknadMedJournalId.correlationId)
            .body(
                """
                    { 
                        "journalpostId": "${nySøknadMedJournalId.journalId}",
                        "saksreferanse": "${nySøknadMedJournalId.sakId}",
                        "aktoerId": "${nySøknadMedJournalId.aktørId}", 
                        "tema": "SUP",
                        "behandlesAvApplikasjon": "SUPSTONAD",
                        "oppgavetype": "BEH_SAK",
                        "behandlingstema": "$TEMA_SU_UFØR_FLYKTNING", 
                        "behandlingstype": "$TYPE_FØRSTEGANGSSØKNAD", 
                        "aktivDato": "${LocalDate.now()}",
                        "fristFerdigstillelse": "${LocalDate.now().plusDays(30)}",
                        "prioritet": "NORM"
                     }
         """.trimIndent()
            ).responseString()

        return result.fold(
            { resultat ->
                JSONObject(resultat).getLong("id")
            },
            { throw RuntimeException("Feil i kallet mot oppgave") }
        )
    }
}
