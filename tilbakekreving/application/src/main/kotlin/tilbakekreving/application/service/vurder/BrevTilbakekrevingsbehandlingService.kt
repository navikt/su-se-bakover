package tilbakekreving.application.service.vurder

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.oppdaterVedtaksbrev
import org.slf4j.LoggerFactory
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.vedtaksbrev.KunneIkkeOppdatereVedtaksbrev
import tilbakekreving.domain.vedtaksbrev.OppdaterVedtaksbrevCommand
import tilgangstyring.application.TilgangstyringService
import java.time.Clock

class BrevTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilgangstyringService,
    private val sakService: SakService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val clock: Clock,
    private val fritekstService: FritekstService,
    private val sessionFactory: SessionFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun lagreBrevtekst(
        command: OppdaterVedtaksbrevCommand,
    ): Either<KunneIkkeOppdatereVedtaksbrev, UnderBehandling.MedKravgrunnlag.Utfylt> {
        val sakId = command.sakId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeOppdatereVedtaksbrev.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere vedtaksbrev for tilbakekrevingsbehandling, fant ikke sak. Kommandoen var: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Vedtaksbrev for tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
            return KunneIkkeOppdatereVedtaksbrev.UlikVersjon.left()
        }

        fritekstService.lagreFritekst(
            FritekstDomain(
                referanseId = command.behandlingId.value,
                sakId = command.sakId,
                type = FritekstType.VEDTAKSBREV_TILBAKEKREVING,
                fritekst = command.brevvalg.fritekst ?: "",
            ),
        )
        val (hendelse, nyState) = sak.oppdaterVedtaksbrev(command, clock)
        sessionFactory.withTransactionContext { tx ->
            tilbakekrevingsbehandlingRepo.lagre(hendelse, meta = command.toDefaultHendelsesMetadata(), tx)
        }

        return nyState.right()
    }
}
