package vilkår.uføre.domain

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.minus
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

const val UFØRETRYGD_MINSTE_ALDER = 18
const val UFØRETRYGD_MAX_ALDER = 67
val UFØRETRYGD_ALDERSINTERVALL = UFØRETRYGD_MINSTE_ALDER..UFØRETRYGD_MAX_ALDER

sealed interface UføreVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Uførhet
    val grunnlag: List<Uføregrunnlag>

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): UføreVilkår

    abstract override fun lagTidslinje(periode: Periode): UføreVilkår

    data object IkkeVurdert : UføreVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<Uføregrunnlag>()
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun lagTidslinje(periode: Periode): IkkeVurdert = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
        override fun slåSammenLikePerioder(): Vilkår = this
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeUføre>,
    ) : UføreVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<Uføregrunnlag> = vurderingsperioder.mapNotNull {
            it.grunnlag
        }
        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOf(Avslagsgrunn.UFØRHET)
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

            fun tryCreate(vurderingsperiode: VurderingsperiodeUføre): Either<UgyldigUførevilkår, Vurdert> {
                return Vurdert(nonEmptyListOf(vurderingsperiode)).right()
            }

            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeUføre>,
            ): Either<UgyldigUførevilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                }

                return Vurdert(vurderingsperioder).right()
            }

            fun fromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodeUføre>,
            ): Either<UgyldigUførevilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed interface UgyldigUførevilkår {
            data object OverlappendeVurderingsperioder : UgyldigUførevilkår
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vurdert {
            val overlapp = vurderingsperioder.any { it.periode overlapper stønadsperiode.periode }

            return if (overlapp) {
                val vurderingerMedOverlapp = lagTidslinje(stønadsperiode.periode).vurderingsperioder
                val manglendePerioder =
                    listOf(stønadsperiode.periode)
                        .minus(vurderingerMedOverlapp.map { it.periode })
                        .sortedBy { it.fraOgMed }.toSet()
                val paired: List<Pair<Periode, VurderingsperiodeUføre?>> = vurderingerMedOverlapp.map {
                    it.periode to it
                }.plus(
                    manglendePerioder.map {
                        it to null
                    },
                )

                paired.fold(mutableListOf<Pair<Periode, VurderingsperiodeUføre>>()) { acc, (periode, vurdering) ->
                    if (vurdering != null) {
                        acc.add((periode to vurdering))
                    } else {
                        val tidligere =
                            vurderingerMedOverlapp.lastOrNull { it.periode starterSamtidigEllerTidligere periode }
                        val senere =
                            vurderingerMedOverlapp.firstOrNull { it.periode starterSamtidigEllerSenere periode }

                        if (tidligere != null) {
                            acc.add(
                                periode to tidligere.oppdaterStønadsperiode(
                                    Stønadsperiode.create(periode = periode),
                                ),
                            )
                        } else if (senere != null) {
                            acc.add(
                                periode to senere.oppdaterStønadsperiode(
                                    Stønadsperiode.create(periode = periode),
                                ),
                            )
                        }
                    }
                    acc
                }.map {
                    it.second
                }.let {
                    Vurdert(vurderingsperioder = it.slåSammenLikePerioder())
                }
            } else {
                val tidligere = stønadsperiode.periode.starterTidligere(
                    vurderingsperioder.map { it.periode }
                        .minByOrNull { it.fraOgMed }!!,
                )

                if (tidligere) {
                    Vurdert(
                        vurderingsperioder = (
                            listOf(
                                vurderingsperioder.minByOrNull { it.periode.fraOgMed }!!
                                    .oppdaterStønadsperiode(stønadsperiode),
                            ).toNonEmptyList()
                            ).slåSammenLikePerioder(),
                    )
                } else {
                    Vurdert(
                        vurderingsperioder = (
                            listOf(
                                vurderingsperioder.maxByOrNull { it.periode.tilOgMed }!!
                                    .oppdaterStønadsperiode(stønadsperiode),
                            ).toNonEmptyList()
                            ).slåSammenLikePerioder(),
                    )
                }
            }
        }

        override fun lagTidslinje(periode: Periode): Vurdert {
            return Vurdert(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }
    }
}
