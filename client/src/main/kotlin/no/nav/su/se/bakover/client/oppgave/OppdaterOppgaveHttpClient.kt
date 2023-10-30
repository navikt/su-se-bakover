package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.PATCH
import no.nav.su.se.bakover.client.isSuccess
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient.Companion.toOppgaveFormat
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock

/**
 * abstaherer vekk en del funksjonalitet fra [OppgaveHttpClient]
 */
internal class OppdaterOppgaveHttpClient(
    private val connectionConfig: ApplicationConfig.ClientsConfig.OppgaveConfig,
    private val clock: Clock,
    private val client: HttpClient,
    private val hentOppgave: (
        oppgaveId: OppgaveId,
        token: String,
    ) -> Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, OppgaveResponse>,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        token: String,
        data: OppdaterOppgaveInfo,
    ): Either<OppgaveFeil, OppdatertOppgaveResponse> {
        return hentOppgave(oppgaveId, token).mapLeft {
            OppgaveFeil.KunneIkkeOppdatereOppgave
        }.flatMap {
            if (it.erFerdigstilt()) {
                log.info("Oppgave $oppgaveId kunne ikke oppdateres fordi den allerede er ferdigstilt")
                OppgaveFeil.KunneIkkeOppdatereOppgave.left()
            } else {
                endreOppgave(it, token, data)
            }
        }
    }

    fun oppdaterBeskrivelse(
        oppgaveId: OppgaveId,
        token: String,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> {
        return hentOppgave(oppgaveId, token).mapLeft {
            OppgaveFeil.KunneIkkeOppdatereOppgave
        }.flatMap {
            oppdaterOppgave(
                oppgaveId = oppgaveId,
                token = token,
                data = OppdaterOppgaveInfo(beskrivelse = beskrivelse),
            ).map { }.mapLeft { OppgaveFeil.KunneIkkeOppdatereOppgave }
        }
    }

    fun lukkOppgave(
        oppgaveId: OppgaveId,
        token: String,
    ): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> {
        return hentOppgave(oppgaveId, token).mapLeft {
            OppgaveFeil.KunneIkkeLukkeOppgave(oppgaveId)
        }.flatMap {
            oppdaterOppgave(
                oppgaveId = oppgaveId,
                token = token,
                data = OppdaterOppgaveInfo(
                    beskrivelse = "Lukket av Supplerende Stønad",
                    status = "FERDIGSTILT",
                ),
            ).map { }.mapLeft { OppgaveFeil.KunneIkkeLukkeOppgave(oppgaveId) }
        }
    }

    private fun endreOppgave(
        oppgave: OppgaveResponse,
        token: String,
        data: OppdaterOppgaveInfo,
    ): Either<OppgaveFeil.KunneIkkeEndreOppgave, OppdatertOppgaveResponse> {
        val internalBeskrivelse =
            "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
            } - ${data.beskrivelse} ---"

        return Either.catch {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${connectionConfig.url}$OPPGAVE_PATH/${oppgave.id}"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal().toString())
                .header("Content-Type", "application/json")
                .PATCH(
                    HttpRequest.BodyPublishers.ofString(
                        serialize(
                            EndreOppgaveRequest(
                                beskrivelse = oppgave.beskrivelse?.let {
                                    internalBeskrivelse.plus("\n\n").plus(oppgave.beskrivelse)
                                } ?: internalBeskrivelse,
                                status = data.status ?: oppgave.status,
                                oppgavetype = data.oppgavetype?.value ?: oppgave.oppgavetype,
                            ),
                        ),
                    ),
                )
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let {
                if (it.isSuccess()) {
                    val loggmelding =
                        "Endret oppgave ${oppgave.id} for sak ${oppgave.saksreferanse} med versjon ${oppgave.versjon} sin status til FERDIGSTILT"
                    log.info("$loggmelding. Response-json finnes i sikkerlogg.")
                    sikkerLogg.info("$loggmelding. Response-json: $it")
                    deserialize<OppdatertOppgaveResponse>(it.body()).right()
                } else {
                    log.error(
                        "Kunne ikke endre oppgave ${oppgave.id} for saksreferanse ${oppgave.saksreferanse} med status=${it.statusCode()} og body=${it.body()}",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    OppgaveFeil.KunneIkkeEndreOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Kunne ikke endre oppgave ${oppgave.id} for saksreferanse ${oppgave.saksreferanse}.", throwable)
            OppgaveFeil.KunneIkkeEndreOppgave
        }.flatten()
    }
}
