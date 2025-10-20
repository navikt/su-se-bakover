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
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeSøkeEtterOppgave
import no.nav.su.se.bakover.domain.oppgave.OboToken
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.SystembrukerToken
import no.nav.su.se.bakover.domain.oppgave.Token
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock

enum class Enhet(val enhet: String) {
    ÅLESUND("4815"),
}

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
    ) -> Either<KunneIkkeSøkeEtterOppgave, OppgaveResponseMedMetadata>,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        token: Token,
        data: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> {
        return hentOppgave(oppgaveId, token.value).mapLeft {
            KunneIkkeOppdatereOppgave.FeilVedHentingAvOppgave
        }.flatMap {
            if (it.oppgaveResponse.erFerdigstilt()) {
                val oppgave = it.oppgaveResponse
                val oppgaveRequest = data
                val loggmelding =
                    "Oppgave var ${oppgave.id} for sak ${oppgave.saksreferanse} med versjon ${oppgave.versjon} sin status til ${oppgave.status}"
                val loggmeldingendrettil =
                    "Oppgave ble ${oppgave.id} for sak ${oppgave.saksreferanse} med versjon ${oppgave.versjon} sin status til ${oppgaveRequest.status} type ${oppgaveRequest.oppgavetype}"
                log.info("Oppgave $oppgaveId kunne ikke oppdateres fordi den allerede er ferdigstilt")
                log.info("Fra: $loggmelding til $loggmeldingendrettil")
                KunneIkkeOppdatereOppgave.OppgaveErFerdigstilt(
                    ferdigstiltTidspunkt = it.oppgaveResponse.ferdigstiltTidspunkt!!.toTidspunkt(),
                    ferdigstiltAv = NavIdentBruker.Saksbehandler(it.oppgaveResponse.endretAv!!),
                    jsonRequest = it.jsonRequest,
                    jsonResponse = it.jsonResponse,
                ).left()
            } else {
                endreOppgave(it.oppgaveResponse, token, data)
            }
        }
    }

    fun lukkOppgave(
        oppgaveId: OppgaveId,
        token: Token,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse> {
        return hentOppgave(oppgaveId, token.value).mapLeft {
            KunneIkkeLukkeOppgave.FeilVedHentingAvOppgave(oppgaveId)
        }.flatMap {
            oppdaterOppgave(
                oppgaveId = oppgaveId,
                token = token,
                data = OppdaterOppgaveInfo(
                    beskrivelse = "Lukket av SU-app (Supplerende Stønad)",
                    status = "FERDIGSTILT",
                    tilordnetRessurs = tilordnetRessurs,
                ),
            ).mapLeft { KunneIkkeLukkeOppgave.FeilVedOppdateringAvOppgave(oppgaveId, it) }
        }
    }

    private fun endreOppgave(
        oppgave: OppgaveResponse,
        token: Token,
        data: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> {
        val internalBeskrivelse =
            "--- ${
                Tidspunkt.now(clock).toOppgaveFormat()
            } - ${data.beskrivelse} ---"

        val tilordnetRessurs = when (val t = data.tilordnetRessurs) {
            is OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs -> null
            is OppdaterOppgaveInfo.TilordnetRessurs.NavIdent -> t.navIdent
            is OppdaterOppgaveInfo.TilordnetRessurs.Uendret -> oppgave.tilordnetRessurs
        }
        return Either.catch {
            val endretAvEnhetsnr = when (token) {
                is SystembrukerToken -> null
                is OboToken -> Enhet.ÅLESUND.enhet
                else -> null
            }
            val requestOppgave = EndreOppgaveRequest(
                beskrivelse = oppgave.beskrivelse?.let {
                    internalBeskrivelse.plus("\n\n").plus(oppgave.beskrivelse)
                } ?: internalBeskrivelse,
                status = data.status ?: oppgave.status,
                oppgavetype = data.oppgavetype?.value ?: oppgave.oppgavetype,
                tilordnetRessurs = tilordnetRessurs,
                endretAvEnhetsnr = endretAvEnhetsnr,
            )
            val requestBody = serialize(
                requestOppgave,
            )

            val request = HttpRequest.newBuilder()
                .uri(URI.create("${connectionConfig.url}$OPPGAVE_PATH/${oppgave.id}"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal().toString())
                .header("Content-Type", "application/json")
                .PATCH(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let {
                if (it.isSuccess()) {
                    val loggmelding =
                        "Oppgave var ${oppgave.id} for sak ${oppgave.saksreferanse} med versjon ${oppgave.versjon} sin status til ${oppgave.status}"
                    val loggmeldingendrettil =
                        "Oppgave ble ${oppgave.id} for sak ${oppgave.saksreferanse} med versjon ${oppgave.versjon} sin status til ${requestOppgave.status} type ${requestOppgave.oppgavetype}"

                    log.info("$loggmelding til $loggmeldingendrettil. Response-json finnes i sikkerlogg.")

                    sikkerLogg.info("$loggmelding. Response-json: $it")

                    OppgaveHttpKallResponse(
                        oppgaveId = oppgave.getOppgaveId(),
                        // bare beskrivelsen er påkrevd å fylles ut. Dersom oppgavetypen ikke skal oppdateres
                        // benytter vi oss av hva som allerede er på oppgaven
                        oppgavetype = data.oppgavetype ?: Oppgavetype.fromString(oppgave.oppgavetype),
                        request = requestBody,
                        response = it.body(),
                        beskrivelse = data.beskrivelse,
                        tilordnetRessurs = tilordnetRessurs,
                        tildeltEnhetsnr = if (tilordnetRessurs == null) null else oppgave.tildeltEnhetsnr,
                    ).right()
                } else {
                    when (it.statusCode()) {
                        401, 403 -> {
                            log.warn(
                                "Auth er ikke gyldig for denne handlingen: ${oppgave.id} for saksreferanse: ${oppgave.saksreferanse} med status=${it.statusCode()}. Se sikkerlogg for mer detaljer",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                            sikkerLogg.warn(
                                "Auth er ikke gyldig for denne handlingen: ${oppgave.id} for saksreferanse: ${oppgave.saksreferanse} med status=${it.statusCode()} og body=${it.body()}",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                        }
                        409 -> {
                            log.warn(
                                "Konflikt, i.e versjonen som sendes med er utdatert fordi noen andre har endret oppgaven: ${oppgave.id} for saksreferanse: ${oppgave.saksreferanse} med status=${it.statusCode()}. Se sikkerlogg for mer detaljer",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                            sikkerLogg.warn(
                                "Konflikt, i.e versjonen som sendes med er utdatert fordi noen andre har endret oppgaven: ${oppgave.id} for saksreferanse: ${oppgave.saksreferanse} med status=${it.statusCode()} og body=${it.body()}",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                        }
                        else -> {
                            log.error(
                                "Ukjent feil: Kunne ikke endre oppgave: ${oppgave.id} for saksreferanse: ${oppgave.saksreferanse} med status=${it.statusCode()}. Se sikkerlogg for mer detaljer",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                            sikkerLogg.error(
                                "Ukjent feil: Kunne ikke endre oppgave: ${oppgave.id} for saksreferanse: ${oppgave.saksreferanse} med status=${it.statusCode()} og body=${it.body()}",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                        }
                    }
                    KunneIkkeOppdatereOppgave.FeilVedRequest.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Kunne ikke endre oppgave ${oppgave.id} for saksreferanse ${oppgave.saksreferanse}.", throwable)
            KunneIkkeOppdatereOppgave.FeilVedRequest
        }.flatten()
    }
}

private data class EndreOppgaveRequest(
    val beskrivelse: String,
    val oppgavetype: String,
    val status: String,
    val tilordnetRessurs: String?,
    val endretAvEnhetsnr: String?,
)
