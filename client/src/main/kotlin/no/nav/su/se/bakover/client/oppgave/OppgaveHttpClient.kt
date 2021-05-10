package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.extensions.either.monad.flatten
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.client.PATCH
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.isSuccess
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
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

    var client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

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
        }.mapLeft { throwable ->
            log.error(
                "Kunne ikke lage onBehalfOfToken for oppgave med klient id ${connectionConfig.clientId}",
                throwable,
            )
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

        return Either.unsafeCatch {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${connectionConfig.url}$oppgavePath"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("X-Correlation-ID", getOrCreateCorrelationId())
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(
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
                        ),
                    ),
                ).build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let {
                val body = it.body()
                if (it.isSuccess()) {
                    log.info("Lagret oppgave i oppgave. status=${it.statusCode()} se sikkerlogg for detaljer")
                    sikkerLogg.info("Lagret oppgave i oppgave. status=${it.statusCode()} body=$body")
                    objectMapper.readValue(body, OppgaveResponse::class.java).getOppgaveId().right()
                } else {
                    log.error("Feil i kallet mot oppgave. status=${it.statusCode()}, body=$body")
                    KunneIkkeOppretteOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Feil i kallet mot oppgave.", throwable)
            KunneIkkeOppretteOppgave
        }.flatten()
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
        return Either.unsafeCatch {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${connectionConfig.url}$oppgavePath/$oppgaveId"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("X-Correlation-ID", getOrCreateCorrelationId())
                .header("Content-Type", "application/json")
                .GET()
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let {
                if (it.isSuccess()) {
                    val oppgave = objectMapper.readValue<OppgaveResponse>(it.body())
                    oppgave.right()
                } else {
                    log.error("Feil ved hent av oppgave $oppgaveId. status=${it.statusCode()} body=${it.body()}")
                    KunneIkkeSøkeEtterOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Feil i kallet mot oppgave.", throwable)
            KunneIkkeSøkeEtterOppgave
        }.flatten()
    }

    private fun lukkOppgave(
        oppgave: OppgaveResponse,
        token: String,
    ): Either<KunneIkkeLukkeOppgave, LukkOppgaveResponse> {
        val beskrivelse =
            "--- ${
            Tidspunkt.now(clock).toOppgaveFormat()
            } - Lukket av Supplerende Stønad ---\nSøknadId : ${oppgave.saksreferanse}"

        return Either.unsafeCatch {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${connectionConfig.url}$oppgavePath/${oppgave.id}"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("X-Correlation-ID", getOrCreateCorrelationId())
                .header("Content-Type", "application/json")
                .PATCH(
                    HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(
                            EndreOppgaveRequest(
                                id = oppgave.id,
                                versjon = oppgave.versjon,
                                beskrivelse = oppgave.beskrivelse?.let {
                                    beskrivelse.plus("\n\n").plus(oppgave.beskrivelse)
                                }
                                    ?: beskrivelse,
                                status = "FERDIGSTILT",
                            ),
                        ),
                    ),
                )
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let {
                if (it.isSuccess()) {
                    val loggmelding =
                        "Endret oppgave ${oppgave.id} med versjon ${oppgave.versjon} sin status til FERDIGSTILT"
                    log.info("$loggmelding. Response-json finnes i sikkerlogg.")
                    sikkerLogg.info("$loggmelding. Response-json: $it")
                    objectMapper.readValue(it.body(), LukkOppgaveResponse::class.java).right()
                } else {
                    log.error("Kunne ikke endre oppgave ${oppgave.id} med status=${it.statusCode()} og body=${it.body()}")
                    KunneIkkeLukkeOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Kunne ikke endre oppgave ${oppgave.id}.", throwable)
            KunneIkkeLukkeOppgave
        }.flatten()
    }

    companion object {
        private fun Tidspunkt.toOppgaveFormat() = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(zoneIdOslo).format(this)
    }

    private object KunneIkkeSøkeEtterOppgave
    private object KunneIkkeLageToken
}
