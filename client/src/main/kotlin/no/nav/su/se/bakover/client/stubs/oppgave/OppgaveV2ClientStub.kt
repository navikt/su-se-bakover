package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Config
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

data object OppgaveV2ClientStub : OppgaveV2Client {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun opprettOppgave(
        config: OppgaveV2Config,
        representertEnhetsnr: String,
        idempotencyKey: UUID,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return stubbedResponse().right().also {
            log.info(
                "OppgaveV2ClientStub oppretter oppgave med oppgavetype={}, representertEnhetsnr={}, idempotencyKey={}",
                config.kategorisering.oppgavetype.kode,
                representertEnhetsnr,
                idempotencyKey,
            )
        }
    }

    override fun opprettOppgaveMedSystembruker(
        config: OppgaveV2Config,
        idempotencyKey: UUID,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return stubbedResponse().right().also {
            log.info(
                "OppgaveV2ClientStub oppretter oppgave med systembruker med oppgavetype={}, idempotencyKey={}",
                config.kategorisering.oppgavetype.kode,
                idempotencyKey,
            )
        }
    }

    private fun stubbedResponse() = OppgaveHttpKallResponse(
        oppgaveId = OppgaveId(STUBBEDOPPGAVEID),
        request = "stubbedRequestBodyV2",
        response = "stubbedResponseBodyV2",
        beskrivelse = "stubbedBeskrivelse",
        oppgavetype = Oppgavetype.BEHANDLE_SAK,
        tilordnetRessurs = "stubbedTilordnetRessurs",
        tildeltEnhetsnr = "stubbedTildeltEnhetsnr",
    )
}
