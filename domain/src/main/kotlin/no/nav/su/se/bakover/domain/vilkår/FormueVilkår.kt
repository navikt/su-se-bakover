package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Inngangsvilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.VurderingsperiodeFormue
import vilkår.formue.domain.firstOrThrowIfMultipleOrEmpty

sealed interface FormueVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Formue
    val grunnlag: List<Formuegrunnlag>

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

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun slåSammenLikePerioder(): Vurdert {
            return Vurdert(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
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
                fromVurderingsperioder(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }

            private fun fromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodeFormue>,
            ): Either<UgyldigFormuevilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigFormuevilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed interface UgyldigFormuevilkår {
            data object OverlappendeVurderingsperioder : UgyldigFormuevilkår
        }
    }
}

fun FormueVilkår.hentFormueGrunnlagForSøknadsbehandling(
    avslagsgrunner: List<Avslagsgrunn>,
): Formuegrunnlag? {
    return when (this) {
        is FormueVilkår.IkkeVurdert -> null
        // TODO(satsfactory_formue) jah: jeg har ikke endret funksjonaliteten i Sats-omskrivningsrunden, men hvorfor sjekker vi avslagsgrunn for å avgjøre dette? De burde jo uansett henge sammen.
        is FormueVilkår.Vurdert -> if (avslagsgrunner.contains(Avslagsgrunn.FORMUE)) this.grunnlag.firstOrThrowIfMultipleOrEmpty() else null
    }
}
