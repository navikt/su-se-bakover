package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.auth.TokenOppslag
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
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

internal const val OPPGAVE_PATH = "/api/v1/oppgaver"

/**
 * Github repo: https://github.com/navikt/oppgave
 * Swagger API: https://oppgave.dev.intern.nav.no/
 * Doc: https://confluence.adeo.no/display/TO/Systemdokumentasjon+Oppgave
 */
internal class OppgaveHttpClient(
    private val connectionConfig: ApplicationConfig.ClientsConfig.OppgaveConfig,
    private val exchange: AzureAd,
    private val tokenoppslagForSystembruker: TokenOppslag,
    private val clock: Clock,
) : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val oppdaterOppgaveHttpClient = OppdaterOppgaveHttpClient(
        connectionConfig = connectionConfig,
        clock = clock,
        client = client,
        hentOppgave = this::hentOppgave,
    )

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return opprettOppgave(config, tokenoppslagForSystembruker.token().value)
    }

    override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return onBehalfOfToken()
            .mapLeft { OppgaveFeil.KunneIkkeOppretteOppgave }
            .flatMap { opprettOppgave(config, it) }
    }

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse> {
        return oppdaterOppgaveHttpClient.lukkOppgave(oppgaveId, tokenoppslagForSystembruker.token().value)
    }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse> {
        return onBehalfOfToken()
            .mapLeft { KunneIkkeLukkeOppgave.FeilVedHentingAvToken(oppgaveId) }
            .flatMap { oppdaterOppgaveHttpClient.lukkOppgave(oppgaveId, it) }
    }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> {
        return onBehalfOfToken()
            .mapLeft { KunneIkkeOppdatereOppgave.FeilVedHentingAvToken }
            .flatMap { oppdaterOppgaveHttpClient.oppdaterBeskrivelse(oppgaveId, it, beskrivelse) }
    }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        oppdatertOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> {
        return onBehalfOfToken()
            .mapLeft { KunneIkkeOppdatereOppgave.FeilVedHentingAvToken }
            .flatMap { oppdaterOppgaveHttpClient.oppdaterOppgave(oppgaveId, it, oppdatertOppgaveInfo) }
    }

    override fun hentOppgave(
        oppgaveId: OppgaveId,
    ): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, Oppgave> {
        return onBehalfOfToken().mapLeft {
            OppgaveFeil.KunneIkkeSøkeEtterOppgave
        }.flatMap { token ->
            hentOppgave(oppgaveId, token)
        }.map {
            it.oppgaveResponse.toDomain()
        }
    }

    override fun hentOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
    ): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, Oppgave> {
        return tokenoppslagForSystembruker.token().value.let {
            hentOppgave(oppgaveId, it)
        }.map {
            it.oppgaveResponse.toDomain()
        }
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
    ): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
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
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}\nPersonhendelse: ${
                    OppgavebeskrivelseMapper.map(
                        config.personhendelsestype,
                    )
                }"

            is OppgaveConfig.Kontrollsamtale ->
                "--- ${
                    Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}"

            is OppgaveConfig.Klage.Klageinstanshendelse ->
                "--- ${
                    Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}\n${
                    OppgavebeskrivelseMapper.map(
                        config,
                    )
                }"

            is OppgaveConfig.Klage ->
                "--- ${
                    Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}"

            is OppgaveConfig.KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale -> {
                "--- ${
                    Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}\nKontrollnotat/Dokumentasjon av oppfølgingssamtale ikke funnet for perioden: ${config.periode.fraOgMed}-${config.periode.tilOgMed}. Maskinell stans kunne ikke gjennomføres."
            }

            is OppgaveConfig.Institusjonsopphold -> {
                "--- ${
                    Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}\nEndring i institusjonsopphold"
            }

            is OppgaveConfig.Tilbakekrevingsbehandling -> {
                "--- ${
                    Tidspunkt.now(clock).toOppgaveFormat()
                } - Opprettet av Supplerende Stønad ---\nSaksnummer : ${config.saksreferanse}"
            }
        }

        return Either.catch {
            val requestBody = serialize(
                OppgaveRequest(
                    journalpostId = config.journalpostId?.toString(),
                    saksreferanse = config.saksreferanse,
                    aktoerId = config.aktørId.toString(),
                    tema = Tema.SUPPLERENDE_STØNAD.value,
                    beskrivelse = beskrivelse,
                    oppgavetype = config.oppgavetype.toString(),
                    behandlingstema = config.behandlingstema?.toString(),
                    behandlingstype = config.behandlingstype.toString(),
                    aktivDato = config.aktivDato,
                    fristFerdigstillelse = config.fristFerdigstillelse,
                    prioritet = "NORM",
                    tilordnetRessurs = config.tilordnetRessurs?.toString(),
                ),
            )

            val request = HttpRequest.newBuilder()
                .uri(URI.create("${connectionConfig.url}$OPPGAVE_PATH"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal().toString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let {
                val body = it.body()
                if (it.isSuccess()) {
                    val oppgaveResponse = deserialize<OppgaveResponse>(body)
                    log.info("Lagret oppgave med id $oppgaveResponse i oppgavesystemet for sak ${config.saksreferanse}. status=${it.statusCode()} se sikkerlogg for detaljer")
                    sikkerLogg.info("Lagret oppgave i oppgave. status=${it.statusCode()} body=$body")

                    OppgaveHttpKallResponse(
                        oppgaveId = oppgaveResponse.getOppgaveId(),
                        oppgavetype = oppgaveResponse.oppgavetype(),
                        request = requestBody,
                        response = body,
                        beskrivelse = beskrivelse,
                    ).right()
                } else {
                    log.error(
                        "Feil i kallet mot oppgave for sak ${config.saksreferanse}. status=${it.statusCode()}, body=$body",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    sikkerLogg.error("Feil i kallet mot oppgave. Requestcontent=$config, ${it.statusCode()}, body=$body")
                    OppgaveFeil.KunneIkkeOppretteOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Feil i kallet mot oppgave for sak ${config.saksreferanse}", throwable)
            OppgaveFeil.KunneIkkeOppretteOppgave
        }.flatten()
    }

    private fun hentOppgave(
        oppgaveId: OppgaveId,
        token: String,
    ): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, OppgaveResponseMedMetadata> {
        return Either.catch {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${connectionConfig.url}$OPPGAVE_PATH/$oppgaveId"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal().toString())
                .header("Content-Type", "application/json")
                .GET()
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let {
                if (it.isSuccess()) {
                    val oppgave = deserialize<OppgaveResponse>(it.body())
                    OppgaveResponseMedMetadata(
                        oppgaveResponse = oppgave,
                        jsonRequest = null,
                        jsonResponse = it.body(),
                    ).right()
                } else {
                    log.error(
                        "Feil ved hent av oppgave $oppgaveId. status=${it.statusCode()} body=${it.body()}",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    OppgaveFeil.KunneIkkeSøkeEtterOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Feil i kallet mot oppgave.", throwable)
            OppgaveFeil.KunneIkkeSøkeEtterOppgave
        }.flatten()
    }

    companion object {
        internal fun Tidspunkt.toOppgaveFormat() = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(zoneIdOslo).format(this)
    }
}
