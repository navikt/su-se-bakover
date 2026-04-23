package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Include
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

data object OppgaveV2ClientStub : OppgaveV2Client {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun opprettOppgave(
        config: OppgaveConfig,
        idempotencyKey: UUID,
        include: Set<OppgaveV2Include>,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return stubbedResponse().right().also {
            log.info("OppgaveV2ClientStub oppretter oppgave: $config, idempotencyKey=$idempotencyKey, include=$include")
        }
    }

    override fun opprettOppgaveMedSystembruker(
        config: OppgaveConfig,
        idempotencyKey: UUID,
        include: Set<OppgaveV2Include>,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> {
        return stubbedResponse().right().also {
            log.info("OppgaveV2ClientStub oppretter oppgave med systembruker: $config, idempotencyKey=$idempotencyKey, include=$include")
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
