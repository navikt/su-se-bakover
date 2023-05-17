package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import java.time.LocalDate
import java.util.UUID

sealed class PersonligOppmøteVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.PersonligOppmøte
    abstract val grunnlag: List<PersonligOppmøteGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PersonligOppmøteVilkår
    abstract override fun slåSammenLikePerioder(): PersonligOppmøteVilkår

    object IkkeVurdert : PersonligOppmøteVilkår() {
        override val vurdering: Vurdering = Vurdering.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<PersonligOppmøteGrunnlag>()
        override val perioder: List<Periode> = emptyList()

        override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): PersonligOppmøteVilkår {
            return this
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert(
        val vurderingsperioder: Nel<VurderingsperiodePersonligOppmøte>,
    ) : PersonligOppmøteVilkår() {

        override val grunnlag: List<PersonligOppmøteGrunnlag> = vurderingsperioder.map { it.grunnlag }
        override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår =
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

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PersonligOppmøteVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): PersonligOppmøteVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }
    }
}

data class VurderingsperiodePersonligOppmøte(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: PersonligOppmøteGrunnlag,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodePersonligOppmøte> {
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
}
