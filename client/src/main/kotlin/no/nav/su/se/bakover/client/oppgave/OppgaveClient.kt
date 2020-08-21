package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
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
        val aktivDato = LocalDate.now()
        val (_, response, result) = "$baseUrl$oppgavePath".httpPost()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                objectMapper.writeValueAsString(
                    OppgaveRequest(
                        journalpostId = config.journalpostId,
                        saksreferanse = config.sakId,
                        aktoerId = config.aktørId.toString(),
                        tema = "SUP",
                        behandlesAvApplikasjon = "SUPSTONAD",
                        oppgavetype = config.oppgavetype.toString(),
                        behandlingstema = config.behandlingstema.toString(),
                        behandlingstype = config.behandlingstype.toString(),
                        aktivDato = aktivDato,
                        fristFerdigstillelse = aktivDato.plusDays(30),
                        prioritet = "NORM"
                    )
                )
            ).responseString()

        return result.fold(
            { json ->
                logger.info("Lagret oppgave i gosys. status=${response.statusCode} body=$json")
                objectMapper.readValue(json, OppgaveResponse::class.java).id.right()
            },
            {
                logger.warn("Feil i kallet mot oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                ClientError(response.statusCode, "Feil i kallet mot oppgave").left()
            }
        )
    }
}
