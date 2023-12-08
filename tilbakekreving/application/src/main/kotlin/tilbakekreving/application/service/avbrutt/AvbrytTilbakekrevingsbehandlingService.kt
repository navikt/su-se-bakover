package tilbakekreving.application.service.avbrutt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.tilgang.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.KanEndres
import tilbakekreving.domain.avbrutt.AvbrytTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.avbrutt.KunneIkkeAvbryte
import tilbakekreving.domain.avbryt
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

class AvbrytTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val clock: Clock,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun avbryt(command: AvbrytTilbakekrevingsbehandlingCommand): Either<KunneIkkeAvbryte, AvbruttTilbakekrevingsbehandling> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeAvbryte.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke underkjenne ${command.behandlingsId.value}, fant ikke sak ${command.sakId}")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Avbrytelse av tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }
        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(command.behandlingsId)
                ?: throw IllegalStateException("Kunne ikke avbryte tilbakekrevingsbehandling ${command.behandlingsId}, fant ikke tilbakekrevingsbehandling på sak. Command: $command")
            ).let {
            it as? KanEndres
                ?: throw IllegalStateException("Kunne ikke avbryte tilbakekrevingsbehandling ${command.behandlingsId}, behandlingen er ikke i tilstanden KanEndres. Command: $command")
        }

        return behandling.avbryt(
            nesteVersjon = sak.versjon.inc(),
            clock = clock,
            utførtAv = command.utførtAv,
            begrunnelse = command.begrunnelse,
        ).let {
            tilbakekrevingsbehandlingRepo.lagre(it.first, command.defaultHendelseMetadata())
            it.second.right()
        }
    }
}
