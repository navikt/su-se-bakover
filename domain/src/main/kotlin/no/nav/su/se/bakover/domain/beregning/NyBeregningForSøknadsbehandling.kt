package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

data class NyBeregningForSøknadsbehandling private constructor(
    val behandlingId: UUID,
    val saksbehandler: Saksbehandler,
    val stønadsperiode: Stønadsperiode,
    val fradrag: List<Fradrag>
) {
    companion object {
        fun create(
            behandlingId: UUID,
            saksbehandler: Saksbehandler,
            stønadsperiode: Stønadsperiode,
            fradrag: List<Fradrag> = emptyList(),
        ): NyBeregningForSøknadsbehandling {
            return tryCreate(
                behandlingId,
                saksbehandler,
                stønadsperiode,
                fradrag
            ).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(
            behandlingId: UUID,
            saksbehandler: Saksbehandler,
            stønadsperiode: Stønadsperiode,
            fradrag: List<Fradrag>,
        ): Either<UgyldigBeregning, NyBeregningForSøknadsbehandling> {
            if (!fradrag.all { stønadsperiode.periode inneholder it.getPeriode() }) {
                return UgyldigBeregning.IkkeLovMedFradragUtenforPerioden.left()
            }

            return NyBeregningForSøknadsbehandling(behandlingId, saksbehandler, stønadsperiode, fradrag).right()
        }
    }

    sealed class UgyldigBeregning {
        object IkkeLovMedFradragUtenforPerioden : UgyldigBeregning()
    }
}
