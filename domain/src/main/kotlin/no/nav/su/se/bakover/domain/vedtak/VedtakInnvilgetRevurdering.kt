package no.nav.su.se.bakover.domain.vedtak

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

data class VedtakInnvilgetRevurdering private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattRevurdering.Innvilget,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    override val beregning: Beregning,
    override val simulering: Simulering,
    override val utbetalingId: UUID30,
    override val dokumenttilstand: Dokumenttilstand,
) : VedtakEndringIYtelse, Revurderingsvedtak {

    init {
        require(periode == behandling.periode)
    }

    companion object {

        fun from(
            revurdering: IverksattRevurdering.Innvilget,
            utbetalingId: UUID30,
            clock: Clock,
        ) = VedtakInnvilgetRevurdering(
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
            behandling: IverksattRevurdering.Innvilget,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            periode: Periode,
            beregning: Beregning,
            simulering: Simulering,
            utbetalingId: UUID30,
            dokumenttilstand: Dokumenttilstand?,
        ) = VedtakInnvilgetRevurdering(
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

    override fun erInnvilget(): Boolean = true
    override fun erOpphør(): Boolean = false
    override fun erStans(): Boolean = false
    override fun erGjenopptak(): Boolean = false

    /**
     *  Dersom dette er en tilbakekreving som avventer kravvgrunnlag, så ønsker vi ikke å sende brev før vi mottar kravgrunnlaget
     *  Brevutsending skjer i [no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService.sendTilbakekrevingsvedtak]
     *  TODO: Er det mulig å flytte denne logikken til ut fra vedtaks-biten til en felles plass?
     */
    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return when (dokumenttilstand) {
            Dokumenttilstand.SKAL_IKKE_GENERERE -> false.also {
                require(!behandling.skalSendeVedtaksbrev())
            }

            Dokumenttilstand.IKKE_GENERERT_ENDA -> !behandling.avventerKravgrunnlag()
            // Her har vi allerede generert brev fra før og ønsker ikke generere et til.
            Dokumenttilstand.GENERERT,
            Dokumenttilstand.JOURNALFØRT,
            Dokumenttilstand.SENDT,
            -> false
        }
    }
}
