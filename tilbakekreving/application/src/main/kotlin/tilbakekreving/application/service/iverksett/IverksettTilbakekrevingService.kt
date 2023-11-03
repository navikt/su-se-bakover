package tilbakekreving.application.service.iverksett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.iverksett
import tilbakekreving.domain.iverksett.IverksettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.iverksett.KunneIkkeIverksette
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

class IverksettTilbakekrevingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val clock: Clock,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun iverksett(
        command: IverksettTilbakekrevingsbehandlingCommand,
    ): Either<KunneIkkeIverksette, IverksattTilbakekrevingsbehandling> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeIverksette.IkkeTilgang(it).left()
        }
        val id = command.tilbakekrevingsbehandlingId

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke iverksette tilbakekrevingsbehandling $id, fant ikke sak ${command.sakId}")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Iverksetting av tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }
        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(id)
                ?: throw IllegalStateException("Kunne ikke iverksette tilbakekrevingsbehandling $id, fant ikke tilbakekrevingsbehandling på sak. Command: $command")
            ).let {
            it as? TilbakekrevingsbehandlingTilAttestering
                ?: throw IllegalStateException("Kunne ikke iverksette tilbakekrevingsbehandling $id, behandlingen er ikke i tilstanden til attestering. Command: $command")
        }

        if (command.utførtAv.navIdent == behandling.sendtTilAttesteringAv.navIdent) {
            log.info("Kunne ikke iverksette tilbakekrevingsbehandling $id, attestant er ikke den samme som den som er satt på behandlingen. Command: $command")
            return KunneIkkeIverksette.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        if (sak.uteståendeKravgrunnlag?.eksternKravgrunnlagId != behandling.kravgrunnlag.eksternKravgrunnlagId) {
            log.info("Kunne ikke iverksette tilbakekrevingsbehandling $id, kravgrunnlaget på behandlingen (eksternKravgrunnlagId ${behandling.kravgrunnlag.eksternKravgrunnlagId}) er ikke det samme som det som er på saken (eksternKravgrunnlagId ${sak.uteståendeKravgrunnlag?.eksternKravgrunnlagId}). Command: $command")
            return KunneIkkeIverksette.KravgrunnlagetHarEndretSeg.left()
        }

        return behandling.iverksett(
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
