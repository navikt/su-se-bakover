package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.client.PATCH
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.getOrCreateCorrelationId
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Tema
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
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
import java.time.format.DateTimeFormatter

internal const val oppgavePath = "/api/v1/oppgaver"

internal class OppgaveHttpClient(
    private val connectionConfig: ApplicationConfig.ClientsConfig.OppgaveConfig,
    private val exchange: AzureAd,
    private val tokenoppslagForSystembruker: TokenOppslag,
    private val clock: Clock,
) : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    var client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> {
        return opprettOppgave(config, tokenoppslagForSystembruker.token().value)
    }

    override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> {
        return onBehalfOfToken()
            .mapLeft { OppgaveFeil.KunneIkkeOppretteOppgave }
            .flatMap { opprettOppgave(config, it) }
    }

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> {
        return lukkOppgave(oppgaveId, tokenoppslagForSystembruker.token().value)
    }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> {
        return onBehalfOfToken()
            .mapLeft { OppgaveFeil.KunneIkkeLukkeOppgave }
            .flatMap { lukkOppgave(oppgaveId, it) }
    }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> {
        return onBehalfOfToken()
            .mapLeft { OppgaveFeil.KunneIkkeOppdatereOppgave }
            .flatMap { oppdaterOppgave(oppgaveId, it, beskrivelse) }
    }

    private fun onBehalfOfToken(): Either<OppgaveFeil.KunneIkkeLageToken, String> {
        return Either.catch {
            exchange.onBehalfOfToken(MDC.get("Authorization"), connectionConfig.clientId)
        }.mapLeft { throwable ->
            log.error(
                "Kunne ikke lage onBehalfOfToken for oppgave med klient id ${connectionConfig.clientId}",
                throwable,
            )
            OppgaveFeil.KunneIkkeLageToken
        }.map {
            it
        }
    }

    private fun opprettOppgave(
        config: OppgaveConfig,
        token: String,
    ): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> {
        val beskrivelse = when (config) {
            is OppgaveConfig.AttesterSøknadsbehandling, is OppgaveConfig.Søknad ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSøknadId : ${config.saksreferanse}"

            is OppgaveConfig.Revurderingsbehandling, is OppgaveConfig.AttesterRevurdering ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}"
            is OppgaveConfig.Personhendelse ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}\nPersonhendelse: ${OppgavebeskrivelseMapper.map(config.personhendelsestype)}"
            is OppgaveConfig.Kontrollsamtale ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}"
            is OppgaveConfig.Klage.Klageinstanshendelse ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}\n${OppgavebeskrivelseMapper.map(config)}"
            is OppgaveConfig.Klage ->
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}"

            is OppgaveConfig.KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale -> {
                "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}\nKontrollnotat ikke funnet for perioden: ${config.periode}. Maskinell stans kunne ikke gjennomføres."
            }
        }

        return Either.catch {
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
                                aktivDato = config.aktivDato,
                                fristFerdigstillelse = config.fristFerdigstillelse,
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
                    sikkerLogg.error("Feil i kallet mot oppgave. Requestcontent=$config, ${it.statusCode()}, body=$body")
                    OppgaveFeil.KunneIkkeOppretteOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Feil i kallet mot oppgave.", throwable)
            OppgaveFeil.KunneIkkeOppretteOppgave
        }.flatten()
    }

    private fun lukkOppgave(oppgaveId: OppgaveId, token: String): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> {
        return hentOppgave(oppgaveId, token).mapLeft {
            OppgaveFeil.KunneIkkeLukkeOppgave
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
    ): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, OppgaveResponse> {
        return Either.catch {
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
                    OppgaveFeil.KunneIkkeSøkeEtterOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Feil i kallet mot oppgave.", throwable)
            OppgaveFeil.KunneIkkeSøkeEtterOppgave
        }.flatten()
    }

    private fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        token: String,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> {
        return hentOppgave(oppgaveId, token).mapLeft {
            OppgaveFeil.KunneIkkeOppdatereOppgave
        }.flatMap {
            if (it.erFerdigstilt()) {
                log.info("Oppgave $oppgaveId kunne ikke oppdateres fordi den allerede er ferdigstilt")
                OppgaveFeil.KunneIkkeOppdatereOppgave.left()
            } else {
                oppdaterOppgave(it, token, beskrivelse).map { }.mapLeft {
                    OppgaveFeil.KunneIkkeOppdatereOppgave
                }
            }
        }
    }

    private fun lukkOppgave(
        oppgave: OppgaveResponse,
        token: String,
    ): Either<OppgaveFeil.KunneIkkeLukkeOppgave, OppdatertOppgaveResponse> {
        return endreOppgave(oppgave, token, "FERDIGSTILT", "Lukket av Supplerende Stønad").mapLeft {
            OppgaveFeil.KunneIkkeLukkeOppgave
        }
    }

    private fun oppdaterOppgave(
        oppgave: OppgaveResponse,
        token: String,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, OppdatertOppgaveResponse> {
        return endreOppgave(oppgave, token, oppgave.status, beskrivelse).mapLeft {
            OppgaveFeil.KunneIkkeOppdatereOppgave
        }
    }

    private fun endreOppgave(
        oppgave: OppgaveResponse,
        token: String,
        status: String,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeEndreOppgave, OppdatertOppgaveResponse> {
        val internalBeskrivelse =
            "--- ${
            Tidspunkt.now(clock).toOppgaveFormat()
            } - $beskrivelse ---"

        return Either.catch {
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
                                    internalBeskrivelse.plus("\n\n").plus(oppgave.beskrivelse)
                                }
                                    ?: internalBeskrivelse,
                                status = status,
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
                    objectMapper.readValue(it.body(), OppdatertOppgaveResponse::class.java).right()
                } else {
                    log.error("Kunne ikke endre oppgave ${oppgave.id} med status=${it.statusCode()} og body=${it.body()}")
                    OppgaveFeil.KunneIkkeEndreOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Kunne ikke endre oppgave ${oppgave.id}.", throwable)
            OppgaveFeil.KunneIkkeEndreOppgave
        }.flatten()
    }

    companion object {
        internal fun Tidspunkt.toOppgaveFormat() = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(zoneIdOslo).format(this)
    }
}
