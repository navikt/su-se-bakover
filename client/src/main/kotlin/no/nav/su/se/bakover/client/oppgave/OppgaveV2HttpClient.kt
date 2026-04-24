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
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Config
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

internal const val OPPGAVE_V2_PATH = "/api/v2/oppgaver"

internal class OppgaveV2HttpClient(
    private val connectionConfig: ApplicationConfig.ClientsConfig.OppgaveConfig,
    private val exchange: AzureAd,
) : OppgaveV2Client {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val oppgaveClientId = connectionConfig.clientId

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun opprettOppgave(
        config: OppgaveV2Config,
        representertEnhetsnr: String,
        idempotencyKey: UUID,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return onBehalfOfToken()
            .mapLeft { KunneIkkeOppretteOppgave }
            .flatMap {
                opprettOppgave(
                    config = config,
                    token = it,
                    representertEnhetsnr = representertEnhetsnr,
                    idempotencyKey = idempotencyKey,
                )
            }
    }

    override fun opprettOppgaveMedSystembruker(
        config: OppgaveV2Config,
        idempotencyKey: UUID,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return opprettOppgave(
            config = config,
            token = exchange.getSystemToken(oppgaveClientId),
            representertEnhetsnr = null,
            idempotencyKey = idempotencyKey,
        )
    }

    private fun onBehalfOfToken(): Either<KunneIkkeLageToken, String> {
        return Either.catch {
            exchange.onBehalfOfToken(JwtToken.BrukerToken.fraCoroutineContext().value, connectionConfig.clientId)
        }.mapLeft { throwable ->
            log.error(
                "Kunne ikke lage onBehalfOfToken for oppgave v2 med klient id ${connectionConfig.clientId}",
                throwable,
            )
            KunneIkkeLageToken
        }
    }

    private data object KunneIkkeLageToken

    private fun opprettOppgave(
        config: OppgaveV2Config,
        token: String,
        representertEnhetsnr: String?,
        idempotencyKey: UUID,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        val requestBody = serialize(config.toOppgaveV2Request(representertEnhetsnr))
        val request = HttpRequest.newBuilder()
            .uri(opprettOppgaveUri())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header(CORRELATION_ID_HEADER, getOrCreateCorrelationIdFromThreadLocal().toString())
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey.toString())
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        return Either.catch {
            client.send(request, HttpResponse.BodyHandlers.ofString()).let { response ->
                val body = response.body()
                if (response.isSuccess()) {
                    val oppgaveResponse = deserialize<OppgaveV2Response>(body)
                    log.info(
                        "Lagret oppgave med id ${oppgaveResponse.id} i oppgave v2. status=${response.statusCode()} se sikkerlogg for detaljer",
                    )
                    sikkerLogg.info("Lagret oppgave i oppgave v2. status=${response.statusCode()} body=$body")

                    OppgaveHttpKallResponse(
                        oppgaveId = OppgaveId(oppgaveResponse.id.toString()),
                        oppgavetype = Oppgavetype.fromString(oppgaveResponse.kategorisering.oppgavetype.kode),
                        request = requestBody,
                        response = body,
                        beskrivelse = oppgaveResponse.beskrivelse ?: config.beskrivelse,
                        tilordnetRessurs = oppgaveResponse.fordeling?.medarbeider?.ident,
                        tildeltEnhetsnr = oppgaveResponse.fordeling?.enhet?.nr,
                    ).right()
                } else {
                    log.error(
                        "Feil i kallet mot oppgave v2. status=${response.statusCode()}. Se sikkerlogg for innhold av body",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    sikkerLogg.error(
                        "Feil i kallet mot oppgave v2. Requestcontent=$config, ${response.statusCode()}, responsebody=$body request=$requestBody idempotencyKey=$idempotencyKey",
                    )
                    KunneIkkeOppretteOppgave.left()
                }
            }
        }.mapLeft { throwable ->
            log.error("Feil i kallet mot oppgave v2", throwable)
            KunneIkkeOppretteOppgave
        }.flatten()
    }

    private fun opprettOppgaveUri(): URI {
        return createOppgaveV2Uri(connectionConfig.url)
    }
}

internal fun createOppgaveV2Uri(baseUrl: String): URI {
    return URI.create("$baseUrl$OPPGAVE_V2_PATH")
}

private fun OppgaveV2Config.toOppgaveV2Request(representertEnhetsnr: String?): OppgaveV2Request {
    return OppgaveV2Request(
        beskrivelse = beskrivelse,
        kategorisering = OppgaveV2Request.Kategorisering(
            tema = OppgaveV2Request.Kode(kategorisering.tema.kode),
            oppgavetype = OppgaveV2Request.Kode(kategorisering.oppgavetype.kode),
            behandlingstema = kategorisering.behandlingstema?.let { OppgaveV2Request.Kode(it.kode) },
            behandlingstype = kategorisering.behandlingstype?.let { OppgaveV2Request.Kode(it.kode) },
        ),
        bruker = bruker?.let {
            OppgaveV2Request.Bruker(
                ident = it.ident,
                type = when (it.type) {
                    OppgaveV2Config.Bruker.Type.PERSON -> OppgaveV2Request.Bruker.Type.PERSON
                },
            )
        },
        aktivDato = aktivDato,
        fristDato = fristDato,
        prioritet = prioritet?.let {
            when (it) {
                OppgaveV2Config.Prioritet.NORMAL -> OppgaveV2Request.Prioritet.NORMAL
                OppgaveV2Config.Prioritet.HOY -> OppgaveV2Request.Prioritet.HOY
                OppgaveV2Config.Prioritet.LAV -> OppgaveV2Request.Prioritet.LAV
            }
        },
        fordeling = fordeling?.let {
            OppgaveV2Request.Fordeling(
                enhet = it.enhet?.let { enhet -> OppgaveV2Request.Fordeling.Enhet(enhet.nr) },
                mappe = it.mappe?.let { mappe -> OppgaveV2Request.Fordeling.Mappe(mappe.id) },
                medarbeider = it.medarbeider?.let { medarbeider -> OppgaveV2Request.Fordeling.Medarbeider(medarbeider.navident) },
            )
        },
        nokkelord = nokkelord,
        arkivreferanse = arkivreferanse?.let { OppgaveV2Request.Arkivreferanse(it.saksnr, it.journalpostId) },
        tilknyttetSystem = tilknyttetSystem,
        meta = if (representertEnhetsnr != null || meta != null) {
            OppgaveV2Request.Meta(
                representerer = representertEnhetsnr?.let {
                    OppgaveV2Request.Meta.Representerer(
                        enhet = OppgaveV2Request.Meta.Representerer.Enhet(it),
                    )
                },
                kommentar = meta?.kommentar,
            )
        } else {
            null
        },
    )
}
