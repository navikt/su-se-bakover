package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeSøkeEtterOppgave
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data object OppgaveClientStub : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> =
        OppgaveHttpKallResponse(
            oppgaveId = OppgaveId("stubbedOppgaveId"),
            request = "stubbedRequestBody",
            response = "stubbedResponseBody",
            beskrivelse = "stubbedBeskrivelse",
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            tilordnetRessurs = "stubbedTilordnetRessurs",
            tildeltEnhetsnr = "stubbedTildeltEnhetsnr",
        ).right().also { log.info("OppgaveClientStub oppretter oppgave: $config") }

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse> =
        OppgaveHttpKallResponse(
            oppgaveId = OppgaveId("stubbedOppgaveId"),
            request = "stubbedRequestBody",
            response = "stubbedResponseBody",
            beskrivelse = "stubbedBeskrivelse",
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            tilordnetRessurs = "stubbedTilordnetRessurs",
            tildeltEnhetsnr = "stubbedTildeltEnhetsnr",
        ).right().also { log.info("OppgaveClientStub oppretter oppgave med systembruker: $config") }

    override fun lukkOppgave(
        oppgaveId: OppgaveId,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse> =
        OppgaveHttpKallResponse(
            oppgaveId = oppgaveId,
            request = "stubbedRequest",
            response = "stubbedResponse",
            beskrivelse = "stubbedBeskrivelse",
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            tilordnetRessurs = when (tilordnetRessurs) {
                is OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs -> null
                is OppdaterOppgaveInfo.TilordnetRessurs.NavIdent -> tilordnetRessurs.navIdent
                is OppdaterOppgaveInfo.TilordnetRessurs.Uendret -> "stubbedTilordnetRessurs"
            },
            tildeltEnhetsnr = when (tilordnetRessurs) {
                OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs -> null
                is OppdaterOppgaveInfo.TilordnetRessurs.NavIdent -> "stubbedTildeltEnhetsnr"
                OppdaterOppgaveInfo.TilordnetRessurs.Uendret -> "stubbedTildeltEnhetsnr"
            },
        ).right().also { log.info("OppgaveClientStub lukker oppgave med oppgaveId: $oppgaveId") }

    override fun lukkOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse> =
        OppgaveHttpKallResponse(
            oppgaveId = oppgaveId,
            request = "stubbedRequest",
            response = "stubbedResponse",
            beskrivelse = "stubbedBeskrivelse",
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            tilordnetRessurs = when (tilordnetRessurs) {
                is OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs -> null
                is OppdaterOppgaveInfo.TilordnetRessurs.NavIdent -> tilordnetRessurs.navIdent
                is OppdaterOppgaveInfo.TilordnetRessurs.Uendret -> "stubbedTilordnetRessurs"
            },
            tildeltEnhetsnr = when (tilordnetRessurs) {
                OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs -> null
                is OppdaterOppgaveInfo.TilordnetRessurs.NavIdent -> "stubbedTildeltEnhetsnr"
                OppdaterOppgaveInfo.TilordnetRessurs.Uendret -> "stubbedTildeltEnhetsnr"
            },
        ).right().also { log.info("OppgaveClientStub lukker oppgave med systembruker og oppgaveId: $oppgaveId") }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        oppdatertOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> = OppgaveHttpKallResponse(
        oppgaveId = oppgaveId,
        request = "stubbedRequest",
        response = "stubbedResponse",
        beskrivelse = "stubbedBeskrivelse",
        oppgavetype = Oppgavetype.BEHANDLE_SAK,
        tilordnetRessurs = "stubbedTilordnetRessurs",
        tildeltEnhetsnr = "stubbedTildeltEnhetsnr",
    ).right()
        .also { log.info("OppgaveClientStub oppdaterer oppgave $oppgaveId med beskrivelse: ${oppdatertOppgaveInfo.beskrivelse}") }

    override fun oppdaterOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        oppdatertOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> = OppgaveHttpKallResponse(
        oppgaveId = oppgaveId,
        request = "stubbedRequest",
        response = "stubbedResponse",
        beskrivelse = "stubbedBeskrivelse",
        oppgavetype = Oppgavetype.BEHANDLE_SAK,
        tilordnetRessurs = "stubbedTilordnetRessurs",
        tildeltEnhetsnr = "stubbedTildeltEnhetsnr",
    ).right().also { log.info("OppgaveClientStub oppdaterer oppgave $oppgaveId med data: $oppdatertOppgaveInfo") }

    override fun hentOppgave(
        oppgaveId: OppgaveId,
    ): Either<KunneIkkeSøkeEtterOppgave, Oppgave> {
        return Oppgave(
            id = oppgaveId,
            versjon = 1,
            status = Oppgave.Oppgavestatus.Opprettet,
        ).also { log.info("OppgaveClientStub hentet oppgave $it") }.right()
    }

    override fun hentOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
    ): Either<KunneIkkeSøkeEtterOppgave, Oppgave> {
        return Oppgave(
            id = oppgaveId,
            versjon = 1,
            status = Oppgave.Oppgavestatus.Opprettet,
        ).also { log.info("OppgaveClientStub hentet oppgave $it") }.right()
    }
}
