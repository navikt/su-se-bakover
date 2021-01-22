package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import java.util.UUID

data class NyBeregningForSøknadsbehandling(
    val behandlingId: UUID,
    val saksbehandler: Saksbehandler,
    val periode: Stønadsperiode,
    val fradrag: List<Fradrag>
) {
    companion object {
        fun create(behandlingId: UUID, saksbehandler: Saksbehandler, periode: Stønadsperiode, fradrag: List<Fradrag>): NyBeregningForSøknadsbehandling {
            return tryCreate(behandlingId, saksbehandler, periode, fradrag).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(behandlingId: UUID, saksbehandler: Saksbehandler, periode: Stønadsperiode, fradrag: List<Fradrag>) : Either<UgyldigBeregning, NyBeregningForSøknadsbehandling> {
            if ( !fradrag.all {periode.periode inneholder it.getPeriode() } ) { return UgyldigBeregning.IkkeLovMedFradragUtenforPerioden.left() }

            return NyBeregningForSøknadsbehandling(behandlingId, saksbehandler, periode, fradrag).right()
        }
    }

    sealed class UgyldigBeregning {
        object IkkeLovMedFradragUtenforPerioden : UgyldigBeregning()
    }
}
