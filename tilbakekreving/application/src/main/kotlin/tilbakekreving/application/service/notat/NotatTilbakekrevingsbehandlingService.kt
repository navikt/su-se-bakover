package tilbakekreving.application.service.notat

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.tilgang.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.KanLeggeTilNotat
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.leggTilNotat
import tilbakekreving.domain.notat.KunneIkkeLagreNotat
import tilbakekreving.domain.notat.OppdaterNotatCommand
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

class NotatTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun lagreNotat(command: OppdaterNotatCommand): Either<KunneIkkeLagreNotat, UnderBehandling> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeLagreNotat.IkkeTilgang(it).left()
        }
        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke lagre notat for tilbakekrevingsbehandling ${command.behandlingId}, fant ikke sak ${command.sakId}")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Lagre notat for tilbakekreving ${command.behandlingId} - Sakens (${command.sakId}) versjon (${sak.versjon}) er ulik saksbehandlers versjon")
        }

        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(command.behandlingId)
                ?: throw IllegalStateException("Kunne ikke lagre notat for tilbakekrevingsbehandling (${command.behandlingId}). Fant ikke Tilbakekrevingsbehandling, sak ${command.sakId}")
            ).let {
            it as? KanLeggeTilNotat
                ?: throw IllegalStateException("Kunne ikke lagre notat for tilbakekrevingsbehandling (${command.behandlingId}), behandlingen er ikke i riktig tilstand. sak ${command.sakId}")
        }

        return behandling.leggTilNotat(
            command = command,
            tidligereHendelsesId = behandling.hendelseId,
            nesteVersjon = sak.versjon.inc(),
            clock = clock,
        ).let {
            tilbakekrevingsbehandlingRepo.lagre(it.first)
            it.second.right()
        }
    }
}
