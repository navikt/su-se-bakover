package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPatch
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
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
    private val tokenoppslagForSystembruker: TokenOppslag,
    private val clock: Clock,
) : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    val client = HttpClient(Java) {
        expectSuccess = false
    }

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId> {
        return opprettOppgave(config, tokenoppslagForSystembruker.token())
    }

    override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId> {
        return onBehalfOfToken()
            .mapLeft { KunneIkkeOppretteOppgave }
            .flatMap { opprettOppgave(config, it) }
    }

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit> {
        return lukkOppgave(oppgaveId, tokenoppslagForSystembruker.token())
    }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit> {
        return onBehalfOfToken()
            .mapLeft { KunneIkkeLukkeOppgave }
            .flatMap { lukkOppgave(oppgaveId, it) }
    }

    private fun onBehalfOfToken(): Either<KunneIkkeLageToken, String> {
        return Either.unsafeCatch {
            exchange.onBehalfOfToken(MDC.get("Authorization"), connectionConfig.clientId)
        }.mapLeft {
            log.error("Kunne ikke lage onBehalfOfToken for oppgave med klient id ${connectionConfig.clientId}, $it")
            KunneIkkeLageToken
        }.map {
            it
        }
    }

    private fun opprettOppgave(config: OppgaveConfig, token: String): Either<KunneIkkeOppretteOppgave, OppgaveId> {
        val aktivDato = LocalDate.now(clock)

        val beskrivelse = when (config) {
            is OppgaveConfig.Attestering, is OppgaveConfig.Saksbehandling ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSøknadId : ${config.saksreferanse}"

            is OppgaveConfig.Revurderingsbehandling, is OppgaveConfig.AttesterRevurdering, is OppgaveConfig.Forhåndsvarsling ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}"
        }

        return runBlocking {
            Either.catch {
                client.post<HttpResponse>("${connectionConfig.url}$oppgavePath") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                        append(HttpHeaders.Accept, "application/json")
                        append("X-Correlation-ID", getOrCreateCorrelationId())
                    }
                    contentType(ContentType.Application.Json)
                    body = objectMapper.writeValueAsString(
                        OppgaveRequest(
                            journalpostId = config.journalpostId?.toString(),
                            saksreferanse = config.saksreferanse,
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
                            tilordnetRessurs = config.tilordnetRessurs?.toString(),
                        ),
                    )
                }
            }.mapLeft { throwable ->
                log.error("Feil i kallet mot oppgave.", throwable)
                KunneIkkeOppretteOppgave
            }.flatMap { response ->
                val body = response.readText()
                if (response.status.isSuccess()) {
                    log.info("Lagret oppgave i oppgave. status=${response.status} se sikkerlogg for detaljer")
                    sikkerLogg.info("Lagret oppgave i oppgave. status=${response.status} body=$body")
                    objectMapper.readValue(body, OppgaveResponse::class.java).getOppgaveId().right()
                } else {
                    log.error("Feil i kallet mot oppgave. status=${response.status} se sikkerlogg for detaljer  body=$body")
                    sikkerLogg.error(
                        "Feil i kallet mot oppgave. status=${response.status} body=$body",
                    )
                    KunneIkkeOppretteOppgave.left()
                }
            }
        }
    }

    private fun lukkOppgave(oppgaveId: OppgaveId, token: String): Either<KunneIkkeLukkeOppgave, Unit> {
        return hentOppgave(oppgaveId, token).mapLeft {
            KunneIkkeLukkeOppgave
        }.flatMap {
            if (it.erFerdigstilt()) {
                log.info("Oppgave $oppgaveId er allerede lukket")
                Unit.right()
            } else {
                lukkOppgave(it, token).map { }
            }
        }
    }

    private fun hentOppgave(
        oppgaveId: OppgaveId,
        token: String,
    ): Either<KunneIkkeSøkeEtterOppgave, OppgaveResponse> {
        val (_, _, result) = "${connectionConfig.url}$oppgavePath/$oppgaveId".httpGet()
            .authentication().bearer(token)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", getOrCreateCorrelationId())
            .responseString()
        return result.fold(
            { responseJson ->
                val oppgave = objectMapper.readValue<OppgaveResponse>(responseJson)
                oppgave.right()
            },
            {
                log.error(
                    "Feil ved hent av oppgave $oppgaveId. status=${it.response.statusCode} body=${String(it.response.data)}",
                    it,
                )
                KunneIkkeSøkeEtterOppgave.left()
            },
        )
    }

    private fun lukkOppgave(
        oppgave: OppgaveResponse,
        token: String,
    ): Either<KunneIkkeLukkeOppgave, LukkOppgaveResponse> {
        val beskrivelse =
            "--- ${
            Tidspunkt.now(clock).toOppgaveFormat()
            } - Lukket av Supplerende Stønad ---\nSøknadId : ${oppgave.saksreferanse}"
        val (_, response, result) = "${connectionConfig.url}$oppgavePath/${oppgave.id}".httpPatch()
            .authentication().bearer(token)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", getOrCreateCorrelationId())
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
