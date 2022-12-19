package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate
import java.util.UUID

sealed class InstitusjonsoppholdVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.Institusjonsopphold

    abstract override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): InstitusjonsoppholdVilkår
    abstract override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår

    object IkkeVurdert : InstitusjonsoppholdVilkår() {
        override val vurdering: Vurdering = Vurdering.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val perioder: List<Periode> = emptyList()

        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår {
            return this
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
    ) : InstitusjonsoppholdVilkår() {

        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår {
            return copy(
                vurderingsperioder = Tidslinje(
                    periode = periode,
                    objekter = vurderingsperioder,
                ).tidslinje.toNonEmptyList(),
            )
        }

        override val erInnvilget: Boolean = vurderingsperioder.all { it.vurdering == Vurdering.Innvilget }

        override val erAvslag: Boolean = vurderingsperioder.any { it.vurdering == Vurdering.Avslag }

        override val vurdering: Vurdering =
            if (erInnvilget) Vurdering.Innvilget else if (erAvslag) Vurdering.Avslag else Vurdering.Uavklart

        override val perioder: Nel<Periode> = vurderingsperioder.minsteAntallSammenhengendePerioder()

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder
                .filter { it.vurdering == Vurdering.Avslag }
                .map { it.periode.fraOgMed }
                .minByOrNull { it }
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
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
                return tryCreate(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }
            }
        }

        sealed class UgyldigInstitisjonsoppholdVilkår {
            object OverlappendeVurderingsperioder : UgyldigInstitisjonsoppholdVilkår()
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
    }
}

data class VurderingsperiodeInstitusjonsopphold private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeInstitusjonsopphold> {

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
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
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
