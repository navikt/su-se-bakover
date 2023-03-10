package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.fullstendigOrThrow
import no.nav.su.se.bakover.domain.grunnlag.lagTidslinje
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
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
data class VedtakPåTidslinje(
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

    override fun copy(args: CopyArgs.Tidslinje): VedtakPåTidslinje =
        when (args) {
            CopyArgs.Tidslinje.Full -> {
                copy(
                    periode = periode,
                    grunnlagsdata = Grunnlagsdata.create(
                        bosituasjon = grunnlagsdata.bosituasjon.map {
                            it.fullstendigOrThrow()
                        }.lagTidslinje(periode),
                        /**
                         * TODO("dette ser ut som en bug, bør vel kvitte oss med forventet inntekt her og")
                         * Se hva vi gjør for NyPeriode litt lenger ned i denne funksjonen.
                         * Dersom dette grunnlaget brukes til en ny revurdering ønsker vi vel at forventet inntekt
                         * utledes på nytt fra grunnlaget i uførevilkåret? Kan vi potensielt ende opp med at vi
                         * får dobbelt opp med fradrag for forventet inntekt?
                         */
                        fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.mapNotNull {
                            it.copy(args = CopyArgs.Snitt(periode))
                        },
                    ),
                    vilkårsvurderinger = vilkårsvurderinger.lagTidslinje(periode),
                    originaltVedtak = originaltVedtak,
                )
            }

            is CopyArgs.Tidslinje.NyPeriode -> {
                copy(
                    periode = args.periode,
                    grunnlagsdata = Grunnlagsdata.create(
                        bosituasjon = grunnlagsdata.bosituasjon.map {
                            it.fullstendigOrThrow()
                        }.lagTidslinje(args.periode),
                        fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag.filterNot {
                            it.fradragstype == Fradragstype.ForventetInntekt
                        }.mapNotNull {
                            it.copy(args = CopyArgs.Snitt(args.periode))
                        },
                    ),
                    vilkårsvurderinger = vilkårsvurderinger.lagTidslinje(args.periode),
                    originaltVedtak = originaltVedtak,
                )
            }

            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            }
        }

    fun erOpphør(): Boolean {
        return originaltVedtak.erOpphør()
    }

    fun erStans(): Boolean {
        return originaltVedtak.erStans()
    }

    fun erGjenopptak(): Boolean {
        return originaltVedtak.erGjenopptak()
    }

    fun uføreVilkår(): Either<Vilkårsvurderinger.VilkårEksistererIkke, UføreVilkår> {
        return vilkårsvurderinger.uføreVilkår()
    }

    fun lovligOppholdVilkår() = vilkårsvurderinger.lovligOppholdVilkår()

    fun formueVilkår(): FormueVilkår {
        return vilkårsvurderinger.formueVilkår()
    }

    fun utenlandsoppholdVilkår(): UtenlandsoppholdVilkår {
        return vilkårsvurderinger.utenlandsoppholdVilkår()
    }

    fun opplysningspliktVilkår(): OpplysningspliktVilkår {
        return vilkårsvurderinger.opplysningspliktVilkår()
    }

    fun pensjonsVilkår() = vilkårsvurderinger.pensjonsVilkår()

    fun familiegjenforeningvilkår() = vilkårsvurderinger.familiegjenforening()

    fun flyktningVilkår(): Either<Vilkårsvurderinger.VilkårEksistererIkke, FlyktningVilkår> {
        return vilkårsvurderinger.flyktningVilkår()
    }

    fun fastOppholdVilkår() = vilkårsvurderinger.fastOppholdVilkår()

    fun personligOppmøteVilkår(): PersonligOppmøteVilkår {
        return vilkårsvurderinger.personligOppmøteVilkår()
    }
}
