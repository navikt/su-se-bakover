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
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Inngangsvilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder
import java.util.UUID

sealed interface OpplysningspliktVilkår : Vilkår {
    override val vilkår: Inngangsvilkår.Opplysningsplikt get() = Inngangsvilkår.Opplysningsplikt
    val grunnlag: List<Opplysningspliktgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): OpplysningspliktVilkår
    abstract override fun slåSammenLikePerioder(): OpplysningspliktVilkår

    data object IkkeVurdert : OpplysningspliktVilkår, IkkeVurdertVilkår {
        override val grunnlag: List<Opplysningspliktgrunnlag> = emptyList()
        override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): OpplysningspliktVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
    ) : OpplysningspliktVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<Opplysningspliktgrunnlag> = vurderingsperioder.map { it.grunnlag }

        override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder
                    .lagTidslinje()
                    .krympTilPeriode(periode)!!
                    .toNonEmptyList(),
            )
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): OpplysningspliktVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): OpplysningspliktVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {
            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
            ): Vurdert {
                return tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
            }

            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
            ): Either<KunneIkkeLageOpplysningspliktVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return KunneIkkeLageOpplysningspliktVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }
    }
}

data class VurderingsperiodeOpplysningsplikt private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: Opplysningspliktgrunnlag,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeOpplysningsplikt> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeOpplysningsplikt {
        return create(
            id = id,
            opprettet = opprettet,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag.oppdaterPeriode(stønadsperiode.periode),
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeOpplysningsplikt = when (args) {
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
        return other is VurderingsperiodeOpplysningsplikt &&
            vurdering == other.vurdering &&
            grunnlag.erLik(other.grunnlag)
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            grunnlag: Opplysningspliktgrunnlag,
        ): VurderingsperiodeOpplysningsplikt {
            return tryCreate(id, opprettet, periode, grunnlag).getOrElse {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurderingsperiode: Periode,
            grunnlag: Opplysningspliktgrunnlag,
        ): Either<KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode, VurderingsperiodeOpplysningsplikt> {
            if (!vurderingsperiode.fullstendigOverlapp(grunnlag.periode)) {
                return KunneIkkeLageOpplysningspliktVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }
            return VurderingsperiodeOpplysningsplikt(
                id = id,
                opprettet = opprettet,
                grunnlag = grunnlag,
                vurdering = resultatFraBeskrivelse(grunnlag),
                periode = vurderingsperiode,
            ).right()
        }

        private fun resultatFraBeskrivelse(grunnlag: Opplysningspliktgrunnlag): Vurdering {
            return when (grunnlag.beskrivelse) {
                OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon -> {
                    Vurdering.Avslag
                }

                OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon -> {
                    Vurdering.Innvilget
                }
            }
        }
    }
}

sealed interface KunneIkkeLageOpplysningspliktVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLageOpplysningspliktVilkår {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    data object OverlappendeVurderingsperioder : KunneIkkeLageOpplysningspliktVilkår
}
