package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.avrund
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.tidslinje.masker
import java.util.UUID

/**
 * En vurderingsperiode for formue er i dag splittet på resultat.
 * Slik at en vurderingsperiode kun kan opprettes med ett [Vurdering].
 * Resultatet er avhengig av formueverdiene i forhold til formuegrensene.
 * Dvs. at formuesummen vurderingsperioden må være mindre enn alle formuegrensene for at man skal få innvilget.
 * Formuegrensene kan variere innenfor perioden.
 * Dersom formuesummen er mellom [minGrenseverdi,maxGrenseverdi] vil vi i teorien ha et delvis avslag som i praksis blir et fullstendig avslag.
 * For en søknadsbehandling er dette korrekt, men for en stønadsendring (revurdering/regulering/etc.) er ikke dette nødvendigvis korrekt.
 */
data class VurderingsperiodeFormue private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val grunnlag: Formuegrunnlag,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeFormue> {

    init {
        require(periode == grunnlag.periode) {
            "perioden: $periode og grunnlaget sin periode: ${grunnlag.periode} må være lik."
        }
    }

    /**
     * Kun ment brukt fra søknadsbehandling.
     * Resultatet kan endre seg dersom man treffer en annen formuegrense med lavere verdi og man overskrider denne.
     */
    fun oppdaterStønadsperiode(
        stønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
    ): VurderingsperiodeFormue {
        // Vi ønsker å regne ut resultatet på nytt, noe ikke copy-funksjonen gjør.
        return tryCreateFromGrunnlag(
            id = this.id,
            grunnlag = this.grunnlag.oppdaterPeriode(stønadsperiode.periode),
            formuegrenserFactory = formuegrenserFactory,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFormue = when (args) {
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
        return other is VurderingsperiodeFormue &&
            vurdering == other.vurdering &&
            grunnlag.erLik(other.grunnlag)
    }

    fun harEPSFormue(): Boolean {
        return grunnlag.harEPSFormue()
    }

    fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): Nel<VurderingsperiodeFormue> {
        val uendret = masker(perioder)
        val endret = leggTilTomEPSFormueHvisDetMangler().masker(
            perioder = uendret.map { it.periode }
                .minsteAntallSammenhengendePerioder(),
        )
        return Tidslinje(periode, uendret + endret).tidslinje.toNonEmptyList()
    }

    private fun leggTilTomEPSFormueHvisDetMangler(): VurderingsperiodeFormue {
        return copy(grunnlag = grunnlag.leggTilTomEPSFormueHvisDenMangler())
    }

    /**
     * Fjerner formue for EPS for periodene angitt av [perioder]. Identifiserer først alle periodene hvor det ikke
     * skal skje noen endringer og bevarer verdiene for disse (kan være både med/uten EPS). Deretter fjernes
     * EPS for alle periodene, og alle periodene identifisert i første steg maskeres ut. Syr deretter sammen periodene
     * med/uten endring til en komplett oversikt for [periode].
     */
    fun fjernEPSFormue(perioder: List<Periode>): Nel<VurderingsperiodeFormue> {
        val uendret = masker(perioder = perioder)
        val endret =
            fjernEPSFormue().masker(perioder = uendret.map { it.periode }.minsteAntallSammenhengendePerioder())
        return Tidslinje(periode, uendret + endret).tidslinje.toNonEmptyList()
    }

    private fun fjernEPSFormue(): VurderingsperiodeFormue {
        return copy(grunnlag = grunnlag.fjernEPSFormue())
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            grunnlag: Formuegrunnlag,
            periode: Periode,
        ): VurderingsperiodeFormue {
            return tryCreate(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                grunnlag = grunnlag,
                vurderingsperiode = periode,
            ).getOrElse {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            grunnlag: Formuegrunnlag,
            vurderingsperiode: Periode,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeFormue> {
            grunnlag.let {
                if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodeFormue(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
            ).right()
        }

        /**
         * I søknadsbehandlingen har vi mulighet til å huke av for at vi må innhente mer informasjon.
         * Saksbehandleren har fremdeles mulighet til å legge inn verdier for søker og eps.
         * Verdiene til søker og EPS  defaultes til 0, bortsett fra hvis søker har fylt inn et kjøretøy, da saksbehandleren fylle ut dette før hen kan lagre 'må innhente mer informasjon' (dette er en 'feature' inntil videre)
         */
        fun tryCreateFromGrunnlagMåInnhenteMerInformasjon(
            id: UUID = UUID.randomUUID(),
            grunnlag: Formuegrunnlag,
        ): VurderingsperiodeFormue {
            return VurderingsperiodeFormue(
                id = id,
                opprettet = grunnlag.opprettet,
                vurdering = Vurdering.Uavklart,
                grunnlag = grunnlag,
                periode = grunnlag.periode,
            )
        }

        /**
         * Brukes av Revurdering og Søknadsbehandling dersom saksbehandler ikke har huka av for at vi skal innhente
         */
        fun tryCreateFromGrunnlag(
            id: UUID = UUID.randomUUID(),
            grunnlag: Formuegrunnlag,
            formuegrenserFactory: FormuegrenserFactory,
        ): VurderingsperiodeFormue {
            return VurderingsperiodeFormue(
                id = id,
                opprettet = grunnlag.opprettet,
                vurdering = if (grunnlag.periode.måneder().all {
                    grunnlag.sumFormue() <= formuegrenserFactory.forMåned(it).formuegrense.avrund()
                }
                ) {
                    Vurdering.Innvilget
                } else {
                    Vurdering.Avslag
                },
                grunnlag = grunnlag,
                periode = grunnlag.periode,
            )
        }
    }

    sealed class UgyldigVurderingsperiode {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
    }
}
