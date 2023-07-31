package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import java.time.Clock
import java.util.UUID

/**
 * Et opphørsvedtak der vi har sendt linjer til oppdrag.
 * Kan være delvis avkortet.
 * Dersom perioden er avkortet i sin helhet, se [VedtakOpphørAvkorting].
 */
data class VedtakOpphørMedUtbetaling private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattRevurdering.Opphørt,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    override val beregning: Beregning,
    /** Perioden på simuleringen vil kun være de månedene vi skal lage utbetalingslinjer for. S*/
    override val simulering: Simulering,
    override val utbetalingId: UUID30,
    override val dokumenttilstand: Dokumenttilstand,
) : VedtakEndringIYtelse, Opphørsvedtak, Revurderingsvedtak {

    init {
        require(periode == behandling.periode)
    }

    companion object {

        fun from(
            revurdering: IverksattRevurdering.Opphørt,
            utbetalingId: UUID30,
            clock: Clock,
        ) = VedtakOpphørMedUtbetaling(
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
        ) = VedtakOpphørMedUtbetaling(
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

    /** Sjekker både saksbehandlers og attestants simulering. */
    fun førteTilFeilutbetaling(periode: Periode): Boolean =
        behandling.simulering.harFeilutbetalinger(periode) || simulering.harFeilutbetalinger(periode)

    /**
     *  Dersom dette er en tilbakekreving som avventer kravvgrunnlag, så ønsker vi ikke å sende brev før vi mottar kravgrunnlaget
     *  Brevutsending skjer i [no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService.sendTilbakekrevingsvedtak]
     */
    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return when (dokumenttilstand) {
            Dokumenttilstand.SKAL_IKKE_GENERERE -> false.also {
                require(!behandling.skalSendeVedtaksbrev())
            }

            Dokumenttilstand.IKKE_GENERERT_ENDA -> !behandling.avventerKravgrunnlag().also {
                require(behandling.skalSendeVedtaksbrev())
            }
            // Her har vi allerede generert brev fra før og ønsker ikke generere et til.
            Dokumenttilstand.GENERERT,
            Dokumenttilstand.JOURNALFØRT,
            Dokumenttilstand.SENDT,
            -> false
        }
    }
}
