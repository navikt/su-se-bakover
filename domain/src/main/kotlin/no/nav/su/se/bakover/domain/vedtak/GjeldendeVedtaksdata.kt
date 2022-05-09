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
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
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
    private val formuegrenserFactory: FormuegrenserFactory,
) {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering

    private val tidslinje: Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> = vedtakListe
        .lagTidslinje(periode)

    private val vedtakPåTidslinje: List<VedtakSomKanRevurderes.VedtakPåTidslinje> = tidslinje.tidslinje

    val pågåendeAvkortingEllerBehovForFremtidigAvkorting: Boolean =
        vedtakPåTidslinje.any { it.originaltVedtak.harPågåendeAvkorting() || it.originaltVedtak.harIdentifisertBehovForFremtidigAvkorting() }

    private val vilkårsvurderingerFraTidslinje: Vilkårsvurderinger = vedtakPåTidslinje.vilkårsvurderinger()

    // Utleder grunnlagstyper som kan knyttes til vilkår via deres respektive vilkårsvurderinger
    private val uføreGrunnlagOgVilkår: Vilkår.Uførhet =
        when (val vilkårsvurderinger = vilkårsvurderingerFraTidslinje) {
            is Vilkårsvurderinger.Revurdering -> when (val vilkår = vilkårsvurderinger.uføre) {
                Vilkår.Uførhet.IkkeVurdert -> vilkår
                is Vilkår.Uførhet.Vurdert -> vilkår
            }
            is Vilkårsvurderinger.Søknadsbehandling -> when (val vilkår = vilkårsvurderinger.uføre) {
                Vilkår.Uførhet.IkkeVurdert -> vilkår
                is Vilkår.Uførhet.Vurdert -> vilkår
            }
        }

    private val formuevilkårOgGrunnlag: Vilkår.Formue =
        when (val vilkårsvurderinger = vilkårsvurderingerFraTidslinje) {
            is Vilkårsvurderinger.Revurdering -> when (val vilkår = vilkårsvurderinger.formue) {
                is Vilkår.Formue.IkkeVurdert -> vilkår
                is Vilkår.Formue.Vurdert -> vilkår
            }
            is Vilkårsvurderinger.Søknadsbehandling -> when (val vilkår = vilkårsvurderinger.formue) {
                is Vilkår.Formue.IkkeVurdert -> vilkår
                is Vilkår.Formue.Vurdert -> vilkår
            }
        }

    private val utlandsoppholdvilkårOgGrunnlag: UtenlandsoppholdVilkår =
        when (val vilkårsvurderinger = vilkårsvurderingerFraTidslinje) {
            is Vilkårsvurderinger.Revurdering -> when (val vilkår = vilkårsvurderinger.utenlandsopphold) {
                UtenlandsoppholdVilkår.IkkeVurdert -> vilkår
                is UtenlandsoppholdVilkår.Vurdert -> vilkår
            }
            is Vilkårsvurderinger.Søknadsbehandling -> when (val vilkår = vilkårsvurderinger.utenlandsopphold) {
                UtenlandsoppholdVilkår.IkkeVurdert -> vilkår
                is UtenlandsoppholdVilkår.Vurdert -> vilkår
            }
        }

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
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
            uføre = uføreGrunnlagOgVilkår,
            formue = formuevilkårOgGrunnlag,
            utenlandsopphold = utlandsoppholdvilkårOgGrunnlag,
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

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.vilkårsvurderinger(): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = this.map { it.vilkårsvurderinger.uføreVilkår() }
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
        formue = this.map { it.vilkårsvurderinger.formueVilkår() }
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
        utenlandsopphold = this.map { it.vilkårsvurderinger.utenlandsoppholdVilkår() }
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
    )
}

private fun Vilkårsvurderinger.uføreVilkår(): Vilkår.Uførhet {
    return when (this) {
        is Vilkårsvurderinger.Revurdering -> uføre
        is Vilkårsvurderinger.Søknadsbehandling -> uføre
    }
}

private fun Vilkårsvurderinger.formueVilkår(): Vilkår.Formue {
    return when (this) {
        is Vilkårsvurderinger.Revurdering -> formue
        is Vilkårsvurderinger.Søknadsbehandling -> formue
    }
}

private fun Vilkårsvurderinger.utenlandsoppholdVilkår(): UtenlandsoppholdVilkår {
    return when (this) {
        is Vilkårsvurderinger.Revurdering -> utenlandsopphold
        is Vilkårsvurderinger.Søknadsbehandling -> utenlandsopphold
    }
}
