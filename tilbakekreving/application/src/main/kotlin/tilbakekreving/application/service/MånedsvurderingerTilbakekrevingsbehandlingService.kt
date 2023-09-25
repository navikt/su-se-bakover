package tilbakekreving.application.service

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.vurdert.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.LeggTilVurderingerCommand
import tilbakekreving.domain.vurdert.VurdertTilbakekrevingsbehandling
import java.time.Clock

class MånedsvurderingerTilbakekrevingsbehandlingService(
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val hendelseRepo: HendelseRepo,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun vurder(
        command: LeggTilVurderingerCommand,
    ): Either<KunneIkkeVurdereTilbakekrevingsbehandling, VurdertTilbakekrevingsbehandling> {
        val sakId = command.sakId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeVurdereTilbakekrevingsbehandling.IkkeTilgang(it).left()
        }

        // Det er ikke sikkert vi har en hendelse på saken, i så fall genererer vi en ny.
        // val sisteVersjon = hendelseRepo.hentSisteVersjonFraEntitetId(sakId) ?: Hendelsesversjon.ny()

        TODO()
    }
}
