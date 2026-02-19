package tilbakekreving.application.service.vurder

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.hentTilbakekrevingsbehandling
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
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun lagreFritekstTilbakekreving(
        command: OppdaterVedtaksbrevCommand,
    ): Either<KunneIkkeOppdatereVedtaksbrev, Unit> {
        val fritekst = command.brevvalg.fritekst ?: run {
            log.warn("Vedtaksbrev for tilbakekreving har ingen fritekst, lagrer ikke fritekst for sakId={}, behandlingId={}, command={}", command.sakId, command.behandlingId, command)
            return KunneIkkeOppdatereVedtaksbrev.ManglerFritekst.left()
        }
        fritekstService.lagreFritekst(
            FritekstDomain(
                referanseId = command.behandlingId.value,
                sakId = command.sakId,
                type = FritekstType.VEDTAKSBREV_TILBAKEKREVING,
                fritekst = fritekst,
            ),
        )
        return Unit.right()
    }
    fun lagreBrevtekst(
        command: OppdaterVedtaksbrevCommand,
    ): Either<KunneIkkeOppdatereVedtaksbrev, UnderBehandling.MedKravgrunnlag.Utfylt> {
        val sakId = command.sakId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeOppdatereVedtaksbrev.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(sakId).getOrElse { feil ->
            log.error("Kunne ikke oppdatere vedtaksbrev for tilbakekrevingsbehandling, fant ikke sak. sakId={}, command={}", sakId, command, feil)
            return KunneIkkeOppdatereVedtaksbrev.FantIkkeSak.left()
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Vedtaksbrev for tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
            return KunneIkkeOppdatereVedtaksbrev.UlikVersjon.left()
        }
        val tilbakekreving = sak.hentTilbakekrevingsbehandling(command.behandlingId)
            ?: run {
                "Fant ikke tilbakekreving med id ${command.behandlingId}"
                log.error("Fant ikke tilbakekrevingsbehandling for sakId={}, behandlingId={}", command.sakId, command.behandlingId)
                return KunneIkkeOppdatereVedtaksbrev.FantIkkeTilbakekrevingsbehandling.left()
            }

        val eksisterendeBrevvalg = tilbakekreving.vedtaksbrevvalg
        if (eksisterendeBrevvalg == null || eksisterendeBrevvalg::class != command.brevvalg::class) {
            val (hendelse, nyState) = sak.oppdaterVedtaksbrev(command, clock)

            val resultat = lagreFritekstTilbakekreving(command)
            resultat.onLeft { return it.left() }

            tilbakekrevingsbehandlingRepo.lagre(
                hendelse,
                meta = command.toDefaultHendelsesMetadata(),
            )
            return nyState.right()
        }
        lagreFritekstTilbakekreving(command)

        return when (tilbakekreving) {
            is UnderBehandling.MedKravgrunnlag.Utfylt -> tilbakekreving.right()
            else -> throw IllegalStateException("Tilbakekrevingsbehandling er i feil tilstand=${tilbakekreving::class.simpleName}, " + "sakId=${command.sakId}, " + "behandlingId=${command.behandlingId.value}, ")
        }
    }
}
