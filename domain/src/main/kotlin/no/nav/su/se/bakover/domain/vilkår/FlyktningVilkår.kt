package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
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
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import java.time.LocalDate
import java.util.UUID

sealed class FlyktningVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.Flyktning

    abstract override fun lagTidslinje(periode: Periode): FlyktningVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FlyktningVilkår
    abstract override fun slåSammenLikePerioder(): FlyktningVilkår

    object IkkeVurdert : FlyktningVilkår() {
        override val vurdering: Vurdering = Vurdering.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val perioder: List<Periode> = emptyList()

        override fun lagTidslinje(periode: Periode): FlyktningVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): FlyktningVilkår {
            return this
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeFlyktning>,
    ) : FlyktningVilkår() {

        override fun lagTidslinje(periode: Periode): FlyktningVilkår =
            copy(vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList())

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

            fun create(vurderingsperioder: Nel<VurderingsperiodeFlyktning>): Vurdert {
                return tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
            }

            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeFlyktning>,
            ): Either<UgyldigFlyktningVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigFlyktningVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed class UgyldigFlyktningVilkår {
            object OverlappendeVurderingsperioder : UgyldigFlyktningVilkår()
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FlyktningVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): FlyktningVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }
    }
}

data class VurderingsperiodeFlyktning private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeFlyktning> {
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
