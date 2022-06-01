package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.slåSammenPeriodeOgBosituasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.slåSammenPeriodeOgFradrag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate

data class GjeldendeVedtaksdata(
    // TODO Finne et bedre navn. Dette er ikke all vedtaksdata, men kun det som kan Revurderes og Reguleres
    // Perioden vi ønsker å finne gjeldende vedtaksdata for. Det er ikke gitt at man har kontinuerlig ytelse innenfor denne perioden.
    private val periode: Periode,
    private val vedtakListe: NonEmptyList<VedtakSomKanRevurderes>,
    private val clock: Clock,
) {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger.Revurdering.Uføre
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering

    private val tidslinje: Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> = vedtakListe
        .lagTidslinje(periode)

    private val vedtakPåTidslinje: List<VedtakSomKanRevurderes.VedtakPåTidslinje> = tidslinje.tidslinje

    val pågåendeAvkortingEllerBehovForFremtidigAvkorting: Boolean =
        vedtakPåTidslinje.any { it.originaltVedtak.harPågåendeAvkorting() || it.originaltVedtak.harIdentifisertBehovForFremtidigAvkorting() }

    private val vilkårsvurderingerFraTidslinje: Vilkårsvurderinger = vedtakPåTidslinje.vilkårsvurderinger()

    // TODO istedenfor å bruke constructor + init, burde GjeldendeVedtaksdata ha en tryCreate
    init {
        grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = vedtakPåTidslinje.flatMap {
                it.grunnlagsdata.fradragsgrunnlag
            }.slåSammenPeriodeOgFradrag(),
            bosituasjon = vedtakPåTidslinje.flatMap {
                it.grunnlagsdata.bosituasjon
            }.slåSammenPeriodeOgBosituasjon(),
        )
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
            uføre = vilkårsvurderingerFraTidslinje.uføreVilkår(),
            formue = vilkårsvurderingerFraTidslinje.formueVilkår(),
            utenlandsopphold = vilkårsvurderingerFraTidslinje.utenlandsoppholdVilkår(),
            opplysningsplikt = vilkårsvurderingerFraTidslinje.opplysningspliktVilkår(),
        )
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        )
    }

    fun harStans(): Boolean {
        return vedtakPåTidslinje.map { it.originaltVedtak }
            .filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse>().isNotEmpty()
    }

    fun helePeriodenErOpphør(): Boolean {
        return vedtakPåTidslinje.all { it.erOpphør() }
    }

    fun delerAvPeriodenErOpphør(): Boolean {
        return !helePeriodenErOpphør() && vedtakPåTidslinje.any { it.erOpphør() }
    }

    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? =
        tidslinje.gjeldendeForDato(dato)?.originaltVedtak

    fun tidslinjeForVedtakErSammenhengende(): Boolean {
        return vedtaksperioder().minsteAntallSammenhengendePerioder().count() == 1
    }

    fun inneholderOpphørsvedtakMedAvkortingUtenlandsopphold(): Boolean {
        return periode.måneder()
            .mapNotNull { gjeldendeVedtakPåDato(it.fraOgMed) }
            .filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>()
            .any { it.harIdentifisertBehovForFremtidigAvkorting() }
    }

    fun vedtaksperioder(): List<Periode> {
        return vedtakPåTidslinje.map { it.periode }
    }

    /**
     * ##NB! Skal ikke brukes til å utlede perioder for nye vedtak, grunnlagsdata, vilkår eller lignende.
     *
     * Returnerer en periode som representerer tidsintervallet fra start av tidligste vedtak på tidslinjen til og med slutt på seneste vedtak på tidslinjen.
     * Perioden som returneres vil være kontinuerlig, men det er ingen garanti for at grunnlag/vilkår er kontinuerlig for hele denne perioden.
     * @throws NoSuchElementException hvis det ikke eksisterer noen vedtak
     */
    fun periodeFørsteTilOgMedSeneste(): Periode {
        return vedtaksperioder().minsteAntallSammenhengendePerioder().minAndMaxOf()
    }

    /**
     * Returnerer en periode hvor det garanteres at tidslinjen er kontinuerlig.
     * @throws IllegalStateException dersom tidslinjen ikke er kontinuerlig.
     */
    fun garantertSammenhengendePeriode(): Periode {
        return vedtaksperioder().minsteAntallSammenhengendePerioder().singleOrNull()
            ?: throw IllegalStateException("Tidslinjen er ikke sammenhengende, kunne ikke generere sammenhengende periode for alle data")
    }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.vilkårsvurderinger(): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre(
        uføre = this.map { it.uføreVilkår() }
            .filterIsInstance<Vilkår.Uførhet.Vurdert>()
            .flatMap { it.vurderingsperioder }
            .let {
                if (it.isNotEmpty()) {
                    Vilkår.Uførhet.Vurdert.fromVurderingsperioder(vurderingsperioder = NonEmptyList.fromListUnsafe(it))
                        .getOrHandle { throw IllegalArgumentException("Kunne ikke instansiere ${Vilkår.Uførhet.Vurdert::class.simpleName}. Melding: $it") }
                        .slåSammenLikePerioder()
                } else {
                    Vilkår.Uførhet.IkkeVurdert
                }
            },
        formue = this.map { it.formueVilkår() }
            .filterIsInstance<Vilkår.Formue.Vurdert>()
            .flatMap { it.vurderingsperioder }
            .let {
                if (it.isNotEmpty()) {
                    Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                        .slåSammenLikePerioder()
                } else {
                    Vilkår.Formue.IkkeVurdert
                }
            },
        utenlandsopphold = this.map { it.utenlandsoppholdVilkår() }
            .filterIsInstance<UtenlandsoppholdVilkår.Vurdert>()
            .flatMap { it.vurderingsperioder }
            .let {
                if (it.isNotEmpty()) {
                    UtenlandsoppholdVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                        .slåSammenLikePerioder()
                } else {
                    UtenlandsoppholdVilkår.IkkeVurdert
                }
            },
        opplysningsplikt = this.map { it.opplysningspliktVilkår() }
            .filterIsInstance<OpplysningspliktVilkår.Vurdert>()
            .flatMap { it.vurderingsperioder }
            .let {
                if (it.isNotEmpty()) {
                    OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                        .slåSammenLikePerioder()
                } else {
                    OpplysningspliktVilkår.IkkeVurdert
                }
            },
    )
}
