package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate
import java.util.UUID

sealed class OpplysningspliktVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.Opplysningsplikt
    abstract val grunnlag: List<Opplysningspliktgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): OpplysningspliktVilkår
    abstract override fun slåSammenLikePerioder(): OpplysningspliktVilkår

    object IkkeVurdert : OpplysningspliktVilkår() {
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<Opplysningspliktgrunnlag>()
        override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): OpplysningspliktVilkår {
            return this
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
    ) : OpplysningspliktVilkår() {
        override val grunnlag: List<Opplysningspliktgrunnlag> = vurderingsperioder.map { it.grunnlag }
        override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår {
            return copy(
                vurderingsperioder = Nel.fromListUnsafe(
                    Tidslinje(
                        periode = periode,
                        objekter = vurderingsperioder,
                    ).tidslinje,
                ),
            )
        }

        override val erInnvilget: Boolean = vurderingsperioder.all { it.resultat == Resultat.Innvilget }

        override val erAvslag: Boolean = vurderingsperioder.any { it.resultat == Resultat.Avslag }

        override val resultat: Resultat =
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder
                .filter { it.resultat == Resultat.Avslag }
                .map { it.periode.fraOgMed }
                .minByOrNull { it }
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        fun minsteAntallSammenhengendePerioder(): List<Periode> {
            return vurderingsperioder.map { it.periode }.minsteAntallSammenhengendePerioder()
        }

        companion object {
            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
            ): Vurdert {
                return tryCreate(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }
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
    }
}

data class VurderingsperiodeOpplysningsplikt private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: Opplysningspliktgrunnlag,
    override val resultat: Resultat,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeOpplysningsplikt> {

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
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodeOpplysningsplikt &&
            resultat == other.resultat &&
            grunnlag.erLik(other.grunnlag)
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            grunnlag: Opplysningspliktgrunnlag,
        ): VurderingsperiodeOpplysningsplikt {
            return tryCreate(id, opprettet, periode, grunnlag).getOrHandle {
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
                resultat = resultatFraBeskrivelse(grunnlag),
                periode = vurderingsperiode,
            ).right()
        }

        private fun resultatFraBeskrivelse(grunnlag: Opplysningspliktgrunnlag): Resultat {
            return when (grunnlag.beskrivelse) {
                OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon -> {
                    Resultat.Avslag
                }
                OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon -> {
                    Resultat.Innvilget
                }
            }
        }
    }
}

sealed interface KunneIkkeLageOpplysningspliktVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLageOpplysningspliktVilkår {
        object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    object OverlappendeVurderingsperioder : KunneIkkeLageOpplysningspliktVilkår
}
