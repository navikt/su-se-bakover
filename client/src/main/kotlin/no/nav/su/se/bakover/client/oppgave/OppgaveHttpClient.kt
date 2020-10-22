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
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeFerdigstilleOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig.Behandlingstema.SU_UFØRE_FLYKNING
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig.Behandlingstype.FØRSTEGANGSSØKNAD
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig.Oppgavetype.BEHANDLE_SAK
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig.Oppgavetype.TIL_ATTESTERING
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveSøkeResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

internal val oppgavePath = "/api/v1/oppgaver"

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
                        saksreferanse = config.sakId,
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
                log.warn("Feil i kallet mot oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                KunneIkkeOppretteOppgave.left()
            }
        )
    }

    override fun ferdigstillFørstegangsoppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
        return søkEtterOppgave(
            BEHANDLE_SAK.value,
            FØRSTEGANGSSØKNAD.value,
            SU_UFØRE_FLYKNING.value,
            aktørId
        ).mapLeft {
            KunneIkkeFerdigstilleOppgave
        }.flatMap {
            ferdigstillOppgave(it.id, it.versjon)
            Unit.right()
        }
    }

    override fun ferdigstillAttesteringsoppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
        return søkEtterOppgave(
            TIL_ATTESTERING.value,
            FØRSTEGANGSSØKNAD.value,
            SU_UFØRE_FLYKNING.value,
            aktørId
        ).mapLeft {
            KunneIkkeFerdigstilleOppgave
        }.flatMap {
            ferdigstillOppgave(it.id, it.versjon)
            Unit.right()
        }
    }

    private fun ferdigstillOppgave(oppgaveId: Long, versjon: Int): Either<KunneIkkeFerdigstilleOppgave, FerdigstillResponse> {
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
                log.warn("Feil i kallet for å endre oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                KunneIkkeFerdigstilleOppgave.left()
            }
        )
    }

    private fun søkEtterOppgave(oppgavetype: String, behandlingstype: String, behandlingstema: String, aktørId: AktørId): Either<KunneIkkeSøkeEtterOppgave, OppgaveSøkeResultat> {
        val (_, response, result) = "$baseUrl$oppgavePath".httpGet(
            listOf(
                "statuskategori" to "AAPEN",
                "tema" to "SUP",
                "oppgavetype" to oppgavetype,
                "behandlingstype" to behandlingstype,
                "behandlingstema" to behandlingstema,
                "aktoerId" to aktørId.toString()
            )
        )
            .authentication().bearer(tokenOppslag.token())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .responseString()

        return result.fold(
            { json ->
                val oppgaver = objectMapper.readValue<OppgaveSøkResponse>(json).oppgaver
                    .map {
                        OppgaveSøkeResultat(
                            id = it.id,
                            versjon = it.versjon
                        )
                    }

                if (oppgaver.size == 1) {
                    return oppgaver.first().right()
                } else {
                    val melding = "Fant ${oppgaver.size} oppgaver. Klare ikke å bestemme hvilken av de vi skal ha"
                    log.error(melding)
                    KunneIkkeSøkeEtterOppgave.left()
                }
            },
            {
                log.warn("Feil i kallet for å hent oppgave. status=${response.statusCode} body=${String(response.data)}", it)
                KunneIkkeSøkeEtterOppgave.left()
            }
        )
    }

    private object KunneIkkeSøkeEtterOppgave
}
