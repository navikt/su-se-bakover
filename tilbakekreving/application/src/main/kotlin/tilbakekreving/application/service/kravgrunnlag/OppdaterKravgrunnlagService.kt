package tilbakekreving.application.service.kravgrunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.tilgang.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.KanOppdatereKravgrunnlag
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.kravgrunnlag.KunneIkkeOppdatereKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.OppdaterKravgrunnlagCommand
import java.time.Clock

class OppdaterKravgrunnlagService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun oppdater(command: OppdaterKravgrunnlagCommand): Either<KunneIkkeOppdatereKravgrunnlag, KanOppdatereKravgrunnlag> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeOppdatereKravgrunnlag.IkkeTilgang(it).left()
        }
        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere kravgrunnlag for tilbakekrevingsbehandling, fant ikke sak. Command: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Oppdater kravgrunnlag - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }

        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(command.behandlingId)
                ?: throw IllegalStateException("Kunne ikke oppdatere kravgrunnlag for tilbakekrevingsbehandling. Fant ikke Tilbakekrevingsbehandling ${command.behandlingId}. Command: $command")
            ).let {
            it as? KanOppdatereKravgrunnlag
                ?: throw IllegalStateException("Kunne ikke oppdatere kravgrunnlag for tilbakekrevingsbehandling ${command.behandlingId}, behandlingen er ikke i riktig tilstand. Command: $command")
        }

        val uteståendeKravgrunnlag =
            sak.uteståendeKravgrunnlag ?: return KunneIkkeOppdatereKravgrunnlag.FantIkkeUteståendeKravgrunnlag.left()
                .also {
                    log.error("Fant ikke utestående kravgrunnlag for å oppdatere kravgrunnlag på tilbakekrevingsbehandling ${command.behandlingId} for sak ${command.sakId}")
                }

        return behandling.oppdaterKravgrunnlag(
            command = command,
            nesteVersjon = sak.versjon.inc(),
            nyttKravgrunnlag = uteståendeKravgrunnlag,
            clock = clock,
        ).let {
            tilbakekrevingsbehandlingRepo.lagre(it.first, command.toDefaultHendelsesMetadata())
            it.second.right()
        }
    }
}
