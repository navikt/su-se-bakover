package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.unsafeCatch
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal const val oppgavePath = "/api/v1/oppgaver"

internal class OppgaveHttpClient(
    private val connectionConfig: ApplicationConfig.ClientsConfig.OppgaveConfig,
    private val exchange: OAuth,
    private val clock: Clock
) : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private fun onBehalfOfToken(): Either<KunneIkkeLageToken, String> {
        return Either.unsafeCatch {
            exchange.onBehalfOfToken(MDC.get("Authorization"), connectionConfig.clientId)
        }.mapLeft {
            log.error("Kunne ikke lage onBehalfOfToken for oppgave med klient id ${connectionConfig.clientId}")
            KunneIkkeLageToken
        }.map {
            it
        }
    }

    override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId> {
        val aktivDato = LocalDate.now(clock)
        val onBehalfOfToken = onBehalfOfToken().getOrElse {
            return KunneIkkeOppretteOppgave.left()
        }
        val beskrivelse =
            "--- ${Tidspunkt.now(clock).toOppgaveFormat()} - Opprettet av Supplerende Stønad ---\nSøknadId : ${config.søknadId}"
        val (_, response, result) = "${connectionConfig.url}$oppgavePath".httpPost()
            .authentication().bearer(onBehalfOfToken)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                objectMapper.writeValueAsString(
                    OppgaveRequest(
                        journalpostId = config.journalpostId?.toString(),
                        saksreferanse = config.søknadId.toString(),
                        aktoerId = config.aktørId.toString(),
                        tema = Tema.SUPPLERENDE_STØNAD.value,
                        behandlesAvApplikasjon = "SUPSTONAD",
                        beskrivelse = beskrivelse,
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
                sikkerLogg.error(
                    "Feil i kallet mot oppgave. status=${response.statusCode} body=${String(response.data)}",
                    it
                )
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
        val onBehalfOfToken = onBehalfOfToken().getOrElse {
            return KunneIkkeSøkeEtterOppgave.left()
        }
        val (_, _, result) = "${connectionConfig.url}$oppgavePath/$oppgaveId".httpGet()
            .authentication().bearer(onBehalfOfToken)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .responseString()
        return result.fold(
            { responseJson ->
                val oppgave = objectMapper.readValue<OppgaveResponse>(responseJson)
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
        val onBehalfOfToken = onBehalfOfToken().getOrElse {
            return KunneIkkeLukkeOppgave.left()
        }
        val beskrivelse =
            "--- ${Tidspunkt.now(clock).toOppgaveFormat()} - Lukket av Supplerende Stønad ---\nSøknadId : ${oppgave.saksreferanse}"
        val (_, response, result) = "${connectionConfig.url}$oppgavePath/${oppgave.id}".httpPatch()
            .authentication().bearer(onBehalfOfToken)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .body(
                objectMapper.writeValueAsString(
                    EndreOppgaveRequest(
                        id = oppgave.id,
                        versjon = oppgave.versjon,
                        beskrivelse = oppgave.beskrivelse?.let { beskrivelse.plus("\n\n").plus(oppgave.beskrivelse) } ?: beskrivelse,
                        status = "FERDIGSTILT"
                    )
                )
            ).responseString()

        return result.fold(
            { json ->
                val loggmelding =
                    "Endret oppgave ${oppgave.id} med versjon ${oppgave.versjon} sin status til FERDIGSTILT"
                log.info("$loggmelding. Response-json finnes i sikkerlogg.")
                sikkerLogg.info("$loggmelding. Response-json: $json")
                objectMapper.readValue(json, LukkOppgaveResponse::class.java).right()
            },
            {
                log.error(
                    "Feil i kallet for å endre oppgave. status=${response.statusCode} se sikkerlogg for detaljer",
                    it
                )
                sikkerLogg.error(
                    "Feil i kallet for å endre oppgave. status=${response.statusCode} body=${String(response.data)}",
                    it
                )
                KunneIkkeLukkeOppgave.left()
            }
        )
    }

    companion object {
        private fun Tidspunkt.toOppgaveFormat() = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(zoneIdOslo).format(this)
    }

    private object KunneIkkeSøkeEtterOppgave
    private object KunneIkkeLageToken
}
