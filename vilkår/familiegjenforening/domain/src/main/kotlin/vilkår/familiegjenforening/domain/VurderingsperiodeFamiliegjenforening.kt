package vilkår.familiegjenforening.domain

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

data class VurderingsperiodeFamiliegjenforening private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val grunnlag: Grunnlag? = null,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeFamiliegjenforening> {

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFamiliegjenforening {
        return when (args) {
            CopyArgs.Tidslinje.Full -> copy(id = UUID.randomUUID())
            is CopyArgs.Tidslinje.NyPeriode -> copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    override fun erLik(other: Vurderingsperiode) =
        other is VurderingsperiodeFamiliegjenforening && vurdering == other.vurdering && erGrunnlagLik(other.grunnlag)

    override fun copyWithNewId(): Vurderingsperiode {
        return this.copy(id = UUID.randomUUID(), grunnlag = grunnlag?.copyWithNewId())
    }

    private fun erGrunnlagLik(other: Grunnlag?): Boolean {
        return if (grunnlag == null && other == null) {
            true
        } else if (grunnlag == null || other == null) {
            false
        } else {
            grunnlag.erLik(other)
        }
    }

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeFamiliegjenforening {
        return create(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            periode = stønadsperiode.periode,
            grunnlag = null,
        )
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            grunnlag: Grunnlag? = null,
            periode: Periode,
        ) = VurderingsperiodeFamiliegjenforening(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            grunnlag = grunnlag,
            periode = periode,
        )
    }
}
