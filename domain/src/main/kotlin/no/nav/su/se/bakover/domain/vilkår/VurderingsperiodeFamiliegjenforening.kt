package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

data class VurderingsperiodeFamiliegjenforening private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val grunnlag: Grunnlag? = null,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeFamiliegjenforening> {

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFamiliegjenforening {
        return when (args) {
            CopyArgs.Tidslinje.Full -> copy(id = UUID.randomUUID())
            is CopyArgs.Tidslinje.Maskert -> copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            is CopyArgs.Tidslinje.NyPeriode -> copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    override fun erLik(other: Vurderingsperiode) =
        other is VurderingsperiodeFamiliegjenforening && vurdering == other.vurdering && erGrunnlagLik(other.grunnlag)

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
