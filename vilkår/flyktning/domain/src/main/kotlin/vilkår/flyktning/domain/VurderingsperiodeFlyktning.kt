package vilkår.flyktning.domain

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

data class VurderingsperiodeFlyktning private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeFlyktning> {
    override val grunnlag: Grunnlag? = null

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeFlyktning {
        return create(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            periode = stønadsperiode.periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFlyktning = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
            )
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodeFlyktning && vurdering == other.vurdering
    }

    override fun copyWithNewId(): VurderingsperiodeFlyktning = this.copy(id = UUID.randomUUID())

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            periode: Periode,
        ): VurderingsperiodeFlyktning {
            return VurderingsperiodeFlyktning(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                periode = periode,
            )
        }
    }
}
