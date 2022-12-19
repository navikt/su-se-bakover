package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate

sealed class FormueVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.Formue
    abstract val grunnlag: List<Formuegrunnlag>

    abstract fun oppdaterStønadsperiode(
        stønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
    ): FormueVilkår

    abstract override fun lagTidslinje(periode: Periode): FormueVilkår
    abstract override fun slåSammenLikePerioder(): FormueVilkår

    fun harEPSFormue(): Boolean {
        return grunnlag.any { it.harEPSFormue() }
    }

    abstract fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): FormueVilkår

    /**
     * @param perioder vi ønsker å fjerne formue for EPS for. Eventuell formue for EPS som ligger utenfor
     * periodene bevares.
     */
    abstract fun fjernEPSFormue(perioder: List<Periode>): FormueVilkår

    object IkkeVurdert : FormueVilkår() {
        override val vurdering: Vurdering = Vurdering.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val perioder: List<Periode> = emptyList()

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }

        override fun slåSammenLikePerioder(): IkkeVurdert {
            return this
        }

        override fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): FormueVilkår {
            return this
        }

        override val grunnlag = emptyList<Formuegrunnlag>()

        override fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): FormueVilkår = this

        override fun lagTidslinje(periode: Periode): IkkeVurdert {
            return this
        }

        override fun fjernEPSFormue(perioder: List<Periode>): FormueVilkår {
            return this
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeFormue>,
    ) : FormueVilkår() {

        override val perioder: Nel<Periode> = vurderingsperioder.minsteAntallSammenhengendePerioder()

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
        }

        /** Merk at vi ikke kan garantere at det er hull i perioden */
        val periode: Periode = perioder.minAndMaxOf()

        override fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): Vurdert {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return Vurdert(
                vurderingsperioder = this.vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory)
                },
            )
        }

        override fun lagTidslinje(periode: Periode): Vurdert {
            return Vurdert(
                vurderingsperioder = Tidslinje(
                    periode = periode,
                    objekter = vurderingsperioder,
                ).tidslinje.toNonEmptyList(),
            )
        }

        override fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): Vurdert {
            return Vurdert(
                vurderingsperioder = vurderingsperioder.flatMap {
                    it.leggTilTomEPSFormueHvisDetMangler(perioder)
                },
            )
        }

        override fun fjernEPSFormue(perioder: List<Periode>): Vurdert {
            return Vurdert(vurderingsperioder = vurderingsperioder.flatMap { it.fjernEPSFormue(perioder) })
        }

        override val erInnvilget: Boolean =
            vurderingsperioder.all { it.vurdering == Vurdering.Innvilget }

        override val erAvslag: Boolean =
            vurderingsperioder.any { it.vurdering == Vurdering.Avslag }

        override val vurdering: Vurdering =
            if (erInnvilget) Vurdering.Innvilget else if (erAvslag) Vurdering.Avslag else Vurdering.Uavklart

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder.filter { it.vurdering == Vurdering.Avslag }.map { it.periode.fraOgMed }
                .minByOrNull { it }
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun slåSammenLikePerioder(): Vurdert {
            return Vurdert(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override val grunnlag: List<Formuegrunnlag> = vurderingsperioder.map {
            it.grunnlag
        }

        companion object {

            /**
             * @param grunnlag liste med pairs (måInnhenteMerInformasjon -> formuegrunnlag)
             */
            fun tryCreateFromGrunnlag(
                grunnlag: Nel<Pair<Boolean, Formuegrunnlag>>,
                formuegrenserFactory: FormuegrenserFactory,
            ): Either<UgyldigFormuevilkår, Vurdert> {
                val vurderingsperioder = grunnlag.map {
                    if (it.first) {
                        VurderingsperiodeFormue.tryCreateFromGrunnlagMåInnhenteMerInformasjon(
                            grunnlag = it.second,
                        )
                    } else {
                        VurderingsperiodeFormue.tryCreateFromGrunnlag(
                            grunnlag = it.second,
                            formuegrenserFactory = formuegrenserFactory,
                        )
                    }
                }
                return fromVurderingsperioder(vurderingsperioder)
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeFormue>,
            ): Vurdert =
                fromVurderingsperioder(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

            private fun fromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodeFormue>,
            ): Either<UgyldigFormuevilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigFormuevilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed class UgyldigFormuevilkår {
            object OverlappendeVurderingsperioder : UgyldigFormuevilkår()
        }
    }
}
