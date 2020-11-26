package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
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
                        prioritet = "NORM",
                        tilordnetRessurs = config.tilordnetRessurs?.toString()
                    )
                )
            ).responseString()

        return result.fold(
            { json ->
                log.info("Lagret oppgave i oppgave. status=${response.statusCode} se sikkerlogg for detaljer")
                sikkerLogg.info("Lagret oppgave i oppgave. status=${response.statusCode} body=$json")
                objectMapper.readValue(json, OppgaveResponse::class.java).getOppgaveId().right()
            },
            {
                log.error("Feil i kallet mot oppgave. status=${response.statusCode} se sikkerlogg for detaljer", it)
                sikkerLogg.error("Feil i kallet mot oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                KunneIkkeOppretteOppgave.left()
            }
        )
    }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit> {
        return hentOppgave(oppgaveId).mapLeft {
            KunneIkkeLukkeOppgave
        }.flatMap {
            if (it.erFerdigstilt()) {
                Unit.right()
            } else {
                lukkOppgave(it).map { }
            }
        }
    }

    private fun hentOppgave(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, OppgaveResponse> {
        val (_, _, result) = "$baseUrl$oppgavePath/$oppgaveId".httpGet()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .responseString()
        return result.fold(
            { responseJson ->
                println(responseJson)
                val oppgave = objectMapper.readValue<OppgaveResponse>(responseJson)
                println(oppgave)
                oppgave.right()
            },
            {
                KunneIkkeSøkeEtterOppgave.left()
            }
        )
    }

    private fun lukkOppgave(
        oppgave: OppgaveResponse
    ): Either<KunneIkkeLukkeOppgave, LukkOppgaveResponse> {
        val (_, response, result) = "$baseUrl$oppgavePath/${oppgave.id}".httpPatch()
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                objectMapper.writeValueAsString(
                    EndreOppgaveRequest(
                        id = oppgave.id,
                        versjon = oppgave.versjon,
                        beskrivelse = "Lukket av Supplerende Stønad\n\nSaksid : ${oppgave.saksreferanse}",
                        status = "FERDIGSTILT"
                    )
                )
            ).responseString().also { println(it) }

        return result.fold(
            { json ->
                val loggmelding = "Endret oppgave ${oppgave.id} med versjon ${oppgave.versjon} sin status til FERDIGSTILT"
                log.info("$loggmelding. Response-json finnes i sikkerlogg.")
                sikkerLogg.info("$loggmelding. Response-json: $json")
                objectMapper.readValue(json, LukkOppgaveResponse::class.java).right()
            },
            {
                log.error("Feil i kallet for å endre oppgave. status=${response.statusCode} se sikkerlogg for detaljer", it)
                sikkerLogg.error("Feil i kallet for å endre oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                KunneIkkeLukkeOppgave.left()
            }
        )
    }
    private object KunneIkkeSøkeEtterOppgave
}
