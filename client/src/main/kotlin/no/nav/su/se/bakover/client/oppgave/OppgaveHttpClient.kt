package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeFerdigstilleOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

internal const val oppgavePath = "/api/v1/oppgaver"

internal class OppgaveHttpClient(
    private val baseUrl: String,
    private val tokenOppslag: TokenOppslag
) : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId> {
        val aktivDato = LocalDate.now()
        val (_, response, result) = "$baseUrl$oppgavePath".httpPost()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                objectMapper.writeValueAsString(
                    OppgaveRequest(
                        journalpostId = config.journalpostId?.toString(),
                        saksreferanse = config.sakId.toString(),
                        aktoerId = config.aktørId.toString(),
                        tema = "SUP",
                        behandlesAvApplikasjon = "SUPSTONAD",
                        oppgavetype = config.oppgavetype.toString(),
                        behandlingstema = config.behandlingstema?.toString(),
                        behandlingstype = config.behandlingstype.toString(),
                        aktivDato = aktivDato,
                        fristFerdigstillelse = aktivDato.plusDays(30),
                        prioritet = "NORM"
                    )
                )
            ).responseString()

        return result.fold(
            { json ->
                log.info("Lagret oppgave i oppgave. status=${response.statusCode} body=$json")
                objectMapper.readValue(json, OppgaveResponse::class.java).getOppgaveId().right()
            },
            {
                log.error("Feil i kallet mot oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                KunneIkkeOppretteOppgave.left()
            }
        )
    }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
        return hentVersjon(oppgaveId).fold(
            {
                KunneIkkeFerdigstilleOppgave.left()
            },
            {
                ferdigstillOppgave(oppgaveId.toString().toLong(), it)
                Unit.right()
            }
        )
    }

    private fun hentVersjon(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, Int> {
        val (_, _, result) = "$baseUrl$oppgavePath/$oppgaveId".httpGet()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .responseString()
        return result.fold(
            { responseJson ->
                val oppgave = objectMapper.readValue<OppgaveResponse>(responseJson)
                oppgave.versjon.right()
            },
            {
                KunneIkkeSøkeEtterOppgave.left()
            }
        )
    }

    private fun ferdigstillOppgave(
        oppgaveId: Long,
        versjon: Int
    ): Either<KunneIkkeFerdigstilleOppgave, FerdigstillResponse> {
        val (_, response, result) = "$baseUrl$oppgavePath/$oppgaveId".httpPatch()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                objectMapper.writeValueAsString(
                    EndreOppgaveRequest(
                        id = oppgaveId,
                        versjon = versjon,
                        status = "FERDIGSTILT"
                    )
                )
            ).responseString()

        return result.fold(
            { json ->
                log.info("Endret oppgave i oppgave. status=${response.statusCode} body=$json")
                objectMapper.readValue(json, FerdigstillResponse::class.java).right()
            },
            {
                log.error("Feil i kallet for å endre oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                KunneIkkeFerdigstilleOppgave.left()
            }
        )
    }

    private object KunneIkkeSøkeEtterOppgave
}
