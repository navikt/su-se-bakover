package tilbakekreving.application.service.tilAttestering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.tilgang.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.tilAttestering
import tilbakekreving.domain.tilAttestering.KunneIkkeSendeTilAttestering
import tilbakekreving.domain.tilAttestering.TilbakekrevingsbehandlingTilAttesteringCommand
import java.time.Clock

class TilbakekrevingsbehandlingTilAttesteringService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val clock: Clock,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun tilAttestering(
        command: TilbakekrevingsbehandlingTilAttesteringCommand,
    ): Either<KunneIkkeSendeTilAttestering, TilbakekrevingsbehandlingTilAttestering> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeSendeTilAttestering.IkkeTilgang(it).left()
        }
        val id = command.tilbakekrevingsbehandlingId

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke sende tilbakekrevingsbehandling $id til attestering, fant ikke sak ${command.sakId}")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Tilbakekreving til attestering - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }
        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(id)
                ?: throw IllegalStateException("Kunne ikke sende tilbakekrevingsbehandling $id til attestering, fant ikke tilbakekrevingsbehandling på sak ${command.sakId}")
            ).let {
            it as? UnderBehandling.Utfylt
                ?: throw IllegalStateException("Kunne ikke sende tilbakekrevingsbehandling $id til attestering, behandlingen er ikke i tilstanden utfylt")
        }

        if (sak.uteståendeKravgrunnlag != behandling.kravgrunnlag) {
            log.info("Kunne ikke sende tilbakekrevingsbehandling $id til attestering, kravgrunnlaget på behandlingen (eksternKravgrunnlagId ${behandling.kravgrunnlag.eksternKravgrunnlagId}) er ikke det samme som det som er på saken (eksternKravgrunnlagId ${sak.uteståendeKravgrunnlag?.eksternKravgrunnlagId}). For sakId ${sak.id}")
            return KunneIkkeSendeTilAttestering.KravgrunnlagetHarEndretSeg.left()
        }

        return behandling.tilAttestering(
            meta = command.defaultHendelseMetadata(),
            nesteVersjon = sak.versjon.inc(),
            clock = clock,
            utførtAv = command.utførtAv,
        ).let {
            tilbakekrevingsbehandlingRepo.lagre(it)
            it.applyToState(behandling).right()
        }
    }
}
