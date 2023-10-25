package tilbakekreving.application.service.underkjenn

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.IkkeTilgangTilSak
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.underkjenn
import tilbakekreving.domain.underkjent.UnderkjennTilbakekrevingsbehandlingCommand
import java.time.Clock

class UnderkjennTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val clock: Clock,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    sealed interface KunneIkkeUnderkjenne {
        data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeUnderkjenne
        data object UlikVersjon : KunneIkkeUnderkjenne
    }

    fun underkjenn(command: UnderkjennTilbakekrevingsbehandlingCommand): Either<KunneIkkeUnderkjenne, UnderBehandling> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeUnderkjenne.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke underkjenne ${command.behandlingsId.value}, fant ikke sak ${command.sakId}")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Kunne ikke underkjenne ${command.behandlingsId}. Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
            return KunneIkkeUnderkjenne.UlikVersjon.left()
        }
        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(command.behandlingsId)
                ?: throw IllegalStateException("Kunne ikke underkjenne tilbakekrevingsbehandling ${command.behandlingsId}, fant ikke tilbakekrevingsbehandling på sak. Command: $command")
            ).let {
            it as? TilbakekrevingsbehandlingTilAttestering
                ?: throw IllegalStateException("Kunne ikke underkjenne tilbakekrevingsbehandling ${command.behandlingsId}, behandlingen er ikke i tilstanden til attestering. Command: $command")
        }

        return behandling.underkjenn(
            meta = command.toDefaultHendelsesMetadata(),
            nesteVersjon = sak.versjon.inc(),
            clock = clock,
            utførtAv = command.utførtAv,
            grunn = command.grunn,
            kommentar = command.kommentar,
        ).let {
            tilbakekrevingsbehandlingRepo.lagre(it.first)
            it.second.right()
        }
    }
}
