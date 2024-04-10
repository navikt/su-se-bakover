package vilkår.formue.domain

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Inngangsvilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder

sealed interface FormueVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Formue
    override val grunnlag: List<Formuegrunnlag>

    fun oppdaterStønadsperiode(
        stønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
    ): FormueVilkår

    abstract override fun lagTidslinje(periode: Periode): FormueVilkår
    abstract override fun slåSammenLikePerioder(): FormueVilkår

    fun harEPSFormue(): Boolean {
        return grunnlag.any { it.harEPSFormue() }
    }

    fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): FormueVilkår

    /**
     * @param perioder vi ønsker å fjerne formue for EPS for. Eventuell formue for EPS som ligger utenfor
     * periodene bevares.
     */
    fun fjernEPSFormue(perioder: List<Periode>): FormueVilkår

    data object IkkeVurdert : FormueVilkår, IkkeVurdertVilkår {
        override val grunnlag: List<Formuegrunnlag> = emptyList()
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
        override fun slåSammenLikePerioder(): IkkeVurdert = this
        override fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): FormueVilkår = this
        override fun lagTidslinje(periode: Periode): IkkeVurdert = this
        override fun fjernEPSFormue(perioder: List<Periode>): FormueVilkår = this
        override fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): FormueVilkår = this
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeFormue>,
    ) : FormueVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<Formuegrunnlag> = vurderingsperioder.map {
            it.grunnlag
        }

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
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
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

        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOf(Avslagsgrunn.FORMUE)
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun slåSammenLikePerioder(): Vurdert {
            return Vurdert(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override fun copyWithNewId(): Vurdert =
            this.copy(vurderingsperioder = vurderingsperioder.map { it.copyWithNewId() })

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
                fromVurderingsperioder(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }

            private fun fromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodeFormue>,
            ): Either<UgyldigFormuevilkår, Vurdert> {
                return vurderingsperioder.kronologisk().map {
                    Vurdert(it)
                }.mapLeft {
                    UgyldigFormuevilkår.OverlappendeVurderingsperioder
                }
            }
        }

        sealed interface UgyldigFormuevilkår {
            data object OverlappendeVurderingsperioder : UgyldigFormuevilkår
        }
    }
}
