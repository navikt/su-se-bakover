package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

data class VurderingsperiodeLovligOpphold private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val resultat: Resultat,
    override val grunnlag: LovligOppholdGrunnlag?,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeLovligOpphold> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode) = tryCreate(
        id = id,
        opprettet = opprettet,
        resultat = resultat,
        vurderingsperiode = stønadsperiode.periode,
        grunnlag = grunnlag?.oppdaterPeriode(stønadsperiode.periode),
    )

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeLovligOpphold = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag?.copy(args)
            )
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag?.copy(args)
            )
        }
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
        }
    }

    override fun erLik(other: Vurderingsperiode) =
        other is VurderingsperiodeLovligOpphold && resultat == other.resultat && erGrunnlagLik(other.grunnlag)

    private fun erGrunnlagLik(other: Grunnlag?): Boolean {
        return if (grunnlag == null && other == null) {
            true
        } else if (grunnlag == null || other == null) {
            false
        } else grunnlag.erLik(other)
    }

    companion object {
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: LovligOppholdGrunnlag?,
            vurderingsperiode: Periode,
        ): Either<KunneIkkeLageLovligOppholdVilkår, VurderingsperiodeLovligOpphold> {
            grunnlag?.let {
                if (vurderingsperiode != it.periode) return KunneIkkeLageLovligOppholdVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodeLovligOpphold(
                id = id,
                opprettet = opprettet,
                resultat = resultat,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
            ).right()
        }
    }
}
