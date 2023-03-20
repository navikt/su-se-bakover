package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import java.time.Clock
import java.util.UUID

data class VedtakOpphørtRevurdering private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattRevurdering.Opphørt,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    val beregning: Beregning,
    override val simulering: Simulering,
    override val utbetalingId: UUID30,
    override val dokumenttilstand: Dokumenttilstand,
) : VedtakEndringIYtelse {

    init {
        require(periode == behandling.periode)
    }

    companion object {

        fun from(
            revurdering: IverksattRevurdering.Opphørt,
            utbetalingId: UUID30,
            clock: Clock,
        ) = VedtakOpphørtRevurdering(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            behandling = revurdering,
            periode = revurdering.periode,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.attestering.attestant,
            utbetalingId = utbetalingId,
            dokumenttilstand = revurdering.dokumenttilstandForBrevvalg(),
        )

        fun createFromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            behandling: IverksattRevurdering.Opphørt,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            periode: Periode,
            beregning: Beregning,
            simulering: Simulering,
            utbetalingId: UUID30,
            dokumenttilstand: Dokumenttilstand?,
        ) = VedtakOpphørtRevurdering(
            id = id,
            opprettet = opprettet,
            behandling = behandling,
            saksbehandler = saksbehandler,
            attestant = attestant,
            periode = periode,
            beregning = beregning,
            simulering = simulering,
            utbetalingId = utbetalingId,
            dokumenttilstand = dokumenttilstand.setDokumentTilstandBasertPåBehandlingHvisNull(behandling),
        )
    }

    fun utledOpphørsgrunner(clock: Clock) = behandling.utledOpphørsgrunner(clock)

    /**
     *  Dersom dette er en tilbakekreving som avventer kravvgrunnlag, så ønsker vi ikke å sende brev før vi mottar kravgrunnlaget
     *  Brevutsending skjer i [no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService.sendTilbakekrevingsvedtak]
     *  TODO: Er det mulig å flytte denne logikken til ut fra vedtaks-biten til en felles plass?
     */
    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return behandling.skalSendeVedtaksbrev() && !behandling.avventerKravgrunnlag()
    }

    override fun harIdentifisertBehovForFremtidigAvkorting() =
        behandling.avkorting is AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel

    fun harIdentifisertBehovForFremtidigAvkorting(periode: Periode) =
        behandling.avkorting is AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel && behandling.avkorting.periode()
            .overlapper(periode)

    /** Sjekker både saksbehandlers og attestants simulering. */
    fun førteTilFeilutbetaling(periode: Periode): Boolean =
        behandling.simulering.harFeilutbetalinger(periode) || simulering.harFeilutbetalinger(periode)

    override fun accept(visitor: VedtakVisitor) {
        visitor.visit(this)
    }
}
