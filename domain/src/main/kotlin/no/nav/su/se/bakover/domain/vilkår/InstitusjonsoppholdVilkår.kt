package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import vilkår.domain.IkkeVurdertVilkår
import vilkår.domain.Inngangsvilkår
import vilkår.domain.Vilkår
import vilkår.domain.Vurdering
import vilkår.domain.Vurderingsperiode
import vilkår.domain.VurdertVilkår
import vilkår.domain.erLik
import vilkår.domain.grunnlag.Grunnlag
import vilkår.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.domain.kronologisk
import vilkår.domain.slåSammenLikePerioder
import java.util.UUID

sealed interface InstitusjonsoppholdVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Institusjonsopphold

    abstract override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): InstitusjonsoppholdVilkår
    abstract override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår

    data object IkkeVurdert : InstitusjonsoppholdVilkår, IkkeVurdertVilkår {
        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
    ) : InstitusjonsoppholdVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): InstitusjonsoppholdVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
            ): Either<UgyldigInstitisjonsoppholdVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigInstitisjonsoppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }

            fun create(
                vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
            ): Vurdert {
                return tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
            }
        }

        sealed interface UgyldigInstitisjonsoppholdVilkår {
            data object OverlappendeVurderingsperioder : UgyldigInstitisjonsoppholdVilkår
        }
    }
}

data class VurderingsperiodeInstitusjonsopphold private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeInstitusjonsopphold> {

    override val grunnlag: Grunnlag? = null

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeInstitusjonsopphold {
        return create(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            periode = stønadsperiode.periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeInstitusjonsopphold = when (args) {
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
        return other is VurderingsperiodeInstitusjonsopphold && vurdering == other.vurdering
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            periode: Periode,
        ): VurderingsperiodeInstitusjonsopphold {
            return VurderingsperiodeInstitusjonsopphold(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                periode = periode,
            )
        }
    }
}
