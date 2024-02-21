package vilkår.personligoppmøte.domain

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import java.util.UUID

data class VurderingsperiodePersonligOppmøte(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: PersonligOppmøteGrunnlag,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodePersonligOppmøte> {
    override val vurdering: Vurdering = grunnlag.vurdering()

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodePersonligOppmøte {
        return VurderingsperiodePersonligOppmøte(
            id = id,
            opprettet = opprettet,
            grunnlag = grunnlag.oppdaterPeriode(stønadsperiode.periode),
            periode = stønadsperiode.periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodePersonligOppmøte = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag.copy(args),
            )
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag.copy(args),
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodePersonligOppmøte &&
            vurdering == other.vurdering &&
            grunnlag.erLik(other.grunnlag)
    }

    override fun copyWithNewId(): VurderingsperiodePersonligOppmøte =
        this.copy(id = UUID.randomUUID(), grunnlag = grunnlag.copyWithNewId())
}
