package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

internal val oppgavePath = "/api/v1/oppgaver"

internal class OppgaveClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag
) : Oppgave {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun opprettOppgave(config: OppgaveConfig): Either<ClientError, Long> {
        val (_, response, result) = "$baseUrl$oppgavePath".httpPost()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                //language=JSON
                """
                    { 
                        "journalpostId": "${config.journalpostId}",
                        "saksreferanse": "${config.sakId}",
                        "aktoerId": "${config.aktørId}",
                        "tema": "SUP",
                        "behandlesAvApplikasjon": "SUPSTONAD",
                        "oppgavetype": "${config.oppgavetype}",
                        "behandlingstema": "${config.behandlingstema}",
                        "behandlingstype": "${config.behandlingstype}",
                        "aktivDato": "${LocalDate.now()}",
                        "fristFerdigstillelse": "${LocalDate.now().plusDays(30)}",
                        "prioritet": "NORM"
                     }
                """.trimIndent()
            ).responseString()

        return result.fold(
            {
                logger.info("Lagret oppgave i gosys. status=${response.statusCode} body=$it")
                JSONObject(it).getLong("id").right()
            },
            {
                logger.warn("Feil i kallet mot oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                ClientError(response.statusCode, "Feil i kallet mot oppgave").left()
            }
        )
    }
}
