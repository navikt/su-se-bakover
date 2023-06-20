package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.minus
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje

const val uføretrygdMinsteAlder = 18
const val uføretrygdMaxAlder = 67
val uføretrygdAldersIntervall = uføretrygdMinsteAlder..uføretrygdMaxAlder

sealed interface UføreVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Uførhet
    val grunnlag: List<Grunnlag.Uføregrunnlag>

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): UføreVilkår

    abstract override fun lagTidslinje(periode: Periode): UføreVilkår

    object IkkeVurdert : UføreVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<Grunnlag.Uføregrunnlag>()
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

        override val grunnlag: List<Grunnlag.Uføregrunnlag> = vurderingsperioder.mapNotNull {
            it.grunnlag
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun slåSammenLikePerioder(): Vurdert {
            return Vurdert(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {

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
            object OverlappendeVurderingsperioder : UgyldigUførevilkår
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
