package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import java.util.UUID

data class NyBeregningForSøknadsbehandling private constructor(
    val behandlingId: UUID,
    val saksbehandler: Saksbehandler,
    val fradrag: List<Fradrag>,
    val begrunnelse: String?,
) {
    companion object {
        fun create(
            behandlingId: UUID,
            saksbehandler: Saksbehandler,
            stønadsperiode: Stønadsperiode,
            fradrag: List<Fradrag> = emptyList(),
            begrunnelse: String?
        ): NyBeregningForSøknadsbehandling {
            return tryCreate(
                behandlingId,
                saksbehandler,
                stønadsperiode,
                fradrag,
                begrunnelse,
            ).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(
            behandlingId: UUID,
            saksbehandler: Saksbehandler,
            stønadsperiode: Stønadsperiode,
            fradrag: List<Fradrag>,
            begrunnelse: String?,
        ): Either<UgyldigBeregning, NyBeregningForSøknadsbehandling> {
            if (!fradrag.all { stønadsperiode.periode inneholder it.periode }) {
                return UgyldigBeregning.IkkeLovMedFradragUtenforPerioden.left()
            }

            return NyBeregningForSøknadsbehandling(behandlingId, saksbehandler, fradrag, begrunnelse).right()
        }
    }

    sealed class UgyldigBeregning {
        object IkkeLovMedFradragUtenforPerioden : UgyldigBeregning()
    }
}
