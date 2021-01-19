package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.service.behandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.LocalDate
import java.util.UUID

internal class RevurderingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val utbetalingService: UtbetalingService,
) : RevurderingService {

    override fun beregnOgSimuler(
        sakId: UUID,
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregneEllerSimulere, Beregning> {
        return opprettBeregning(
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            fradrag = fradrag
        ).fold(
            ifLeft = { KunneIkkeBeregneEllerSimulere.left() },
            ifRight = { beregning ->
                simuler(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    beregning = beregning
                ).fold(
                    ifLeft = { KunneIkkeBeregneEllerSimulere.left() },
                    ifRight = { beregning.right() }
                )
            },
        )
    }

    private fun opprettBeregning(
        behandlingId: UUID,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregne, Beregning> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)
            ?: return KunneIkkeBeregne.FantIkkeBehandling.left()

        val beregningsperiode = Periode.create(fraOgMed, tilOgMed)

        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = beregningsperiode,
            forventetInntektPerÅr = behandling.behandlingsinformasjon().uførhet?.forventetInntekt?.toDouble() ?: 0.0,
            fradragFraSaksbehandler = fradrag
        )

        val strategy = behandling.behandlingsinformasjon().bosituasjon!!.getBeregningStrategy()

        return strategy.beregn(beregningsgrunnlag).right()
    }

    private fun simuler(
        sakId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        beregning: Beregning
    ): Either<SimuleringFeilet, SimulertOK> {
        return utbetalingService.simulerUtbetaling(
            sakId = sakId,
            saksbehandler = saksbehandler,
            beregning = beregning
        ).fold(
            ifLeft = { it.left() },
            ifRight = { SimulertOK.right() }
        )
    }
}
