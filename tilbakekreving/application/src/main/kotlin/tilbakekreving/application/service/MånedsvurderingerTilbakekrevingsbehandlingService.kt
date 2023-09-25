package tilbakekreving.application.service

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.domain.VurdertTilbakekrevingsbehandling
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.vurdert.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.OppdaterMånedsvurderingerCommand
import java.time.Clock

class MånedsvurderingerTilbakekrevingsbehandlingService(
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val hendelseRepo: HendelseRepo,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun vurder(
        command: OppdaterMånedsvurderingerCommand,
    ): Either<KunneIkkeVurdereTilbakekrevingsbehandling, VurdertTilbakekrevingsbehandling> {
        val sakId = command.sakId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeVurdereTilbakekrevingsbehandling.IkkeTilgang(it).left()
        }

        @Suppress("UNUSED_VARIABLE")
        val tilbakekrevingsbehandling = tilbakekrevingsbehandlingRepo.hentForSak(sakId)
        TODO("implementer")
    }
}
