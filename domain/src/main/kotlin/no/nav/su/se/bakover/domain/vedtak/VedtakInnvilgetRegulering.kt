package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.grunnlag.krevAlleVilkårInnvilget
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import java.time.Clock
import java.util.UUID

data class VedtakInnvilgetRegulering private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattRegulering,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    val beregning: Beregning,
    override val simulering: Simulering,
    override val utbetalingId: UUID30,
) : VedtakEndringIYtelse {

    init {
        behandling.grunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget()
        require(periode == behandling.periode)
    }

    companion object {
        fun from(
            regulering: IverksattRegulering,
            utbetalingId: UUID30,
            clock: Clock,
        ): VedtakInnvilgetRegulering {
            return VedtakInnvilgetRegulering(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = regulering,
                periode = regulering.periode,
                beregning = regulering.beregning,
                simulering = regulering.simulering,
                saksbehandler = regulering.saksbehandler,
                attestant = NavIdentBruker.Attestant(regulering.saksbehandler.toString()),
                utbetalingId = utbetalingId,
            )
        }

        fun createFromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            behandling: IverksattRegulering,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            periode: Periode,
            beregning: Beregning,
            simulering: Simulering,
            utbetalingId: UUID30,
        ): VedtakInnvilgetRegulering {
            return VedtakInnvilgetRegulering(
                id = id,
                opprettet = opprettet,
                behandling = behandling,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode,
                beregning = beregning,
                simulering = simulering,
                utbetalingId = utbetalingId,
            )
        }
    }

    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return false
    }

    override val dokumenttilstand: Dokumenttilstand = behandling.dokumenttilstandForBrevvalg()

    override fun harIdentifisertBehovForFremtidigAvkorting() = false

    override fun accept(visitor: VedtakVisitor) {
        visitor.visit(this)
    }
}
