package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Either
import arrow.core.NonEmptyList
import beregning.domain.fradrag.Fradragstype
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.fullstendigOrThrow
import no.nav.su.se.bakover.domain.grunnlag.lagTidslinje
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.domain.vedtak.VedtakPåTidslinje.Companion.tilVedtakPåTidslinje
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

/**
 * Representerer et vedtak plassert på en tidslinje utledet fra vedtakenes temporale gyldighet.
 * I denne sammenhen er et vedtak ansett som gyldig inntil det utløper eller overskrives (helt/delvis) av et nytt.
 * Ved plassering på tidslinja gjennom [KanPlasseresPåTidslinje], er objektet ansvarlig for at alle periodiserbare
 * opplysninger som ligger til grunn for vedtaket justeres i henhold til aktuell periode gitt av [CopyArgs.Tidslinje].
 */
data class VedtakPåTidslinje private constructor(
    override val opprettet: Tidspunkt,
    override val periode: Periode,
    val grunnlagsdata: Grunnlagsdata,
    val vilkårsvurderinger: Vilkårsvurderinger,
    /**
     * Referanse til det originale vedtaket dette tidslinje-elementet er basert på. Må ikke endres eller benyttes
     * til uthenting av grunnlagsdata.
     */
    val originaltVedtak: VedtakSomKanRevurderes,
) : KanPlasseresPåTidslinje<VedtakPåTidslinje> {

    init {
        require(grunnlagsdata.erUtfylt && vilkårsvurderinger.erVurdert) {
            "Grunnlagsdata og vilkårsvurderinger må være utfylt/vurdert for å kunne opprette et vedtak på tidslinje"
        }
        require(grunnlagsdata.periode == periode && vilkårsvurderinger.periode == periode) {
            "Grunnlagsdata og vilkårsvurderinger må ha samme periode som vedtakstidslinjen"
        }
    }

    override fun copy(args: CopyArgs.Tidslinje): VedtakPåTidslinje = when (args) {
        CopyArgs.Tidslinje.Full -> kopi()
        is CopyArgs.Tidslinje.NyPeriode -> nyPeriode(args.periode)
    }

    /**
     * Reguleringer, og gjenopptak regnes som innvilgelser - I tillegg til Innvilget
     */
    fun erInnvilget(): Boolean = originaltVedtak.erInnvilget()
    fun erOpphør(): Boolean = originaltVedtak.erOpphør()
    fun erStans(): Boolean = originaltVedtak.erStans()
    fun erGjenopptak(): Boolean = originaltVedtak.erGjenopptak()

    fun uføreVilkår(): Either<Vilkårsvurderinger.VilkårEksistererIkke, UføreVilkår> = vilkårsvurderinger.uføreVilkår()
    fun lovligOppholdVilkår() = vilkårsvurderinger.lovligOppholdVilkår()
    fun formueVilkår(): FormueVilkår = vilkårsvurderinger.formueVilkår()
    fun utenlandsoppholdVilkår(): UtenlandsoppholdVilkår = vilkårsvurderinger.utenlandsoppholdVilkår()
    fun opplysningspliktVilkår(): OpplysningspliktVilkår = vilkårsvurderinger.opplysningspliktVilkår()
    fun pensjonsVilkår() = vilkårsvurderinger.pensjonsVilkår()
    fun familiegjenforeningvilkår() = vilkårsvurderinger.familiegjenforening()
    fun flyktningVilkår(): Either<Vilkårsvurderinger.VilkårEksistererIkke, FlyktningVilkår> =
        vilkårsvurderinger.flyktningVilkår()

    fun fastOppholdVilkår() = vilkårsvurderinger.fastOppholdVilkår()
    fun personligOppmøteVilkår(): PersonligOppmøteVilkår = vilkårsvurderinger.personligOppmøteVilkår()

    /**
     * Til bruk for [copy]
     * Til bruk dersom man vil ha denne i sin helhet 'uten' endring.
     */
    private fun kopi(): VedtakPåTidslinje {
        return copy(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = grunnlagsdata.bosituasjon
                    .map { it.fullstendigOrThrow() }
                    .lagTidslinje(periode),
                /*
                 * TODO("dette ser ut som en bug, bør vel kvitte oss med forventet inntekt her og")
                 * Se hva vi gjør for [nyPeriode] i denne funksjonen.
                 * Dersom dette grunnlaget brukes til en ny revurdering ønsker vi vel at forventet inntekt
                 * utledes på nytt fra grunnlaget i uførevilkåret? Kan vi potensielt ende opp med at vi
                 * får dobbelt opp med fradrag for forventet inntekt?
                 */
                fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag
                    .mapNotNull { it.copy(args = CopyArgs.Snitt(periode)) },
            ),
            vilkårsvurderinger = vilkårsvurderinger.lagTidslinje(periode),
        )
    }

    /**
     * Til bruk for [copy]
     * Kopierer seg selv med ny periode
     */
    private fun nyPeriode(p: Periode): VedtakPåTidslinje {
        return copy(
            periode = p,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = grunnlagsdata.bosituasjon
                    .map { it.fullstendigOrThrow() }
                    .lagTidslinje(p),
                fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag
                    .filterNot { it.fradragstype == Fradragstype.ForventetInntekt }
                    .mapNotNull { it.copy(args = CopyArgs.Snitt(p)) },
            ),
            vilkårsvurderinger = vilkårsvurderinger.lagTidslinje(p),
            originaltVedtak = originaltVedtak,
        )
    }

    companion object {
        fun VedtakSomKanRevurderes.tilVedtakPåTidslinje(): VedtakPåTidslinje {
            return VedtakPåTidslinje(
                opprettet = opprettet,
                periode = periode,
                grunnlagsdata = behandling.grunnlagsdata,
                vilkårsvurderinger = behandling.vilkårsvurderinger,
                originaltVedtak = this,
            )
        }

        /**
         * Se [erInnvilget]
         * @return true dersom det finnes minst en innvilgelse i tidslinjen
         */
        fun Tidslinje<VedtakPåTidslinje>?.harInnvilgelse(): Boolean = this != null && this.any { it.erInnvilget() }

        /**
         * Se [erStans]
         * @return true dersom det finnes minst en innvilgelse i tidslinjen
         */
        fun Tidslinje<VedtakPåTidslinje>?.harStans(): Boolean = this != null && this.any { it.erStans() }
    }
}

fun List<VedtakSomKanRevurderes>.lagTidslinje(): Tidslinje<VedtakPåTidslinje>? {
    return mapTilVedtakPåTidslinjeTyper().lagTidslinje()
}

fun NonEmptyList<VedtakSomKanRevurderes>.lagTidslinje(): Tidslinje<VedtakPåTidslinje> {
    return mapTilVedtakPåTidslinjeTyper().lagTidslinje()
}

private fun List<VedtakSomKanRevurderes>.mapTilVedtakPåTidslinjeTyper(): List<VedtakPåTidslinje> {
    return map { it.tilVedtakPåTidslinje() }
}

private fun NonEmptyList<VedtakSomKanRevurderes>.mapTilVedtakPåTidslinjeTyper(): NonEmptyList<VedtakPåTidslinje> {
    return map { it.tilVedtakPåTidslinje() }
}
