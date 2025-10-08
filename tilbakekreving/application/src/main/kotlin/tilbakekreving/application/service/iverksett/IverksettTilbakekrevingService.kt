package tilbakekreving.application.service.iverksett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import org.slf4j.LoggerFactory
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.iverksett
import tilbakekreving.domain.iverksettelse.IverksattHendelseMetadata
import tilbakekreving.domain.iverksettelse.IverksettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.iverksettelse.KunneIkkeIverksette
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilgangstyring.application.TilgangstyringService
import java.time.Clock

class IverksettTilbakekrevingService(
    private val tilgangstyring: TilgangstyringService,
    private val sakService: SakService,
    private val clock: Clock,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val tilbakekrevingsklient: Tilbakekrevingsklient,
    private val sakStatistikkRepo: SakStatistikkRepo,
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

        if (sak.uteståendeKravgrunnlag != behandling.kravgrunnlag) {
            log.info("Kunne ikke iverksette tilbakekrevingsbehandling $id, kravgrunnlaget på behandlingen (eksternKravgrunnlagId ${behandling.kravgrunnlag.eksternKravgrunnlagId}) er ikke det samme som det som er på saken (eksternKravgrunnlagId ${sak.uteståendeKravgrunnlag?.eksternKravgrunnlagId}). Command: $command")
            return KunneIkkeIverksette.KravgrunnlagetHarEndretSeg.left()
        }

        val iverksettelse = behandling.iverksett(
            nesteVersjon = sak.versjon.inc(),
            clock = clock,
            utførtAv = command.utførtAv,
        )

        /**
         * TODO tilbakekreving: At least once idempotency - det kan være slik at sending av vedtak til oppdrag går fint, men vi får ikke persistert hendelsen.
         *   Vi må finne ut hva slags feilmelding oppdrag sender oss når vi sender samme vedtak to ganger og persistere hendelsen når dette skjer.
         */
        return iverksettelse.let {
            val tilbakekrevingsvedtakForsendelse = tilbakekrevingsklient.sendTilbakekrevingsvedtak(
                vurderingerMedKrav = behandling.vurderingerMedKrav,
                attestertAv = command.utførtAv,
            ).getOrElse {
                return KunneIkkeIverksette.KunneIkkeSendeTilbakekrevingsvedtak.left()
            }
            // ved lagring av iverksett hendelsen, skal en jobb starte brev løpet
            tilbakekrevingsbehandlingRepo.lagreIverksattTilbakekrevingshendelse(
                it.first,
                IverksattHendelseMetadata(
                    correlationId = command.correlationId,
                    ident = command.utførtAv,
                    brukerroller = command.brukerroller,
                    tilbakekrevingsvedtakForsendelse = tilbakekrevingsvedtakForsendelse,
                ),
            )
            sakStatistikkRepo.lagreTilbakekrevingStatistikk()
            it.second.right()
        }
    }
}
