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
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
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
    val vilkårsvurderinger: Vilkårsvurderinger.Revurdering
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering

    private val tidslinje: Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> = vedtakListe
        .lagTidslinje(periode)

    private val vedtakPåTidslinje: List<VedtakSomKanRevurderes.VedtakPåTidslinje> = tidslinje.tidslinje

    val pågåendeAvkortingEllerBehovForFremtidigAvkorting: Boolean =
        vedtakPåTidslinje.any { it.originaltVedtak.harPågåendeAvkorting() || it.originaltVedtak.harIdentifisertBehovForFremtidigAvkorting() }

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
        vilkårsvurderinger = vedtakPåTidslinje.let {
            // TODO("vilkårsvurdering_alder mulig vi må/bør gjøre dette på en annen måte")
            when {
                vedtakPåTidslinje.all {
                    it.vilkårsvurderinger is Vilkårsvurderinger.Søknadsbehandling.Uføre ||
                        it.vilkårsvurderinger is Vilkårsvurderinger.Revurdering.Uføre
                } -> {
                    Vilkårsvurderinger.Revurdering.Uføre(
                        uføre = it.uføreVilkår(),
                        lovligOpphold = it.lovligoppholdVilkår(),
                        formue = it.formueVilkår(),
                        utenlandsopphold = it.utenlandsoppholdVilkår(),
                        opplysningsplikt = it.opplysningspliktVilkår(),
                        flyktning = it.flyktningVilkår(),
                        fastOpphold = it.fastOppholdINorgeVilkår(),
                    )
                }
                vedtakPåTidslinje.all {
                    it.vilkårsvurderinger is Vilkårsvurderinger.Søknadsbehandling.Alder ||
                        it.vilkårsvurderinger is Vilkårsvurderinger.Revurdering.Alder
                } -> {
                    Vilkårsvurderinger.Revurdering.Alder(
                        lovligOpphold = it.lovligoppholdVilkår(),
                        formue = it.formueVilkår(),
                        utenlandsopphold = it.utenlandsoppholdVilkår(),
                        opplysningsplikt = it.opplysningspliktVilkår(),
                        pensjon = it.pensjonsVilkår(),
                        familiegjenforening = it.familiegjenforeningvilkår(),
                        fastOpphold = it.fastOppholdINorgeVilkår(),
                    )
                }
                else -> {
                    throw IllegalStateException("Kan ikke hente gjeldende vedtaksdata for blanding av uføre og alder.")
                }
            }
        }
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

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.uføreVilkår(): UføreVilkår {
    return flatMap { vedtak ->
        vedtak.uføreVilkår().fold(
            {
                emptyList()
            },
            {
                when (it) {
                    UføreVilkår.IkkeVurdert -> emptyList()
                    is UføreVilkår.Vurdert -> it.vurderingsperioder
                }
            },
        )
    }.let {
        if (it.isNotEmpty()) {
            UføreVilkår.Vurdert.fromVurderingsperioder(vurderingsperioder = NonEmptyList.fromListUnsafe(it))
                .getOrHandle { throw IllegalArgumentException("Kunne ikke instansiere ${UføreVilkår.Vurdert::class.simpleName}. Melding: $it") }
                .slåSammenLikePerioder()
        } else {
            UføreVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.lovligoppholdVilkår(): LovligOppholdVilkår {
    return map { it.lovligOppholdVilkår() }
        .filterIsInstance<LovligOppholdVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                LovligOppholdVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                    .slåSammenLikePerioder()
            } else {
                LovligOppholdVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.formueVilkår(): FormueVilkår {
    return map { it.formueVilkår() }
        .filterIsInstance<FormueVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                FormueVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                    .slåSammenLikePerioder()
            } else {
                FormueVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.utenlandsoppholdVilkår(): UtenlandsoppholdVilkår {
    return map { it.utenlandsoppholdVilkår() }
        .filterIsInstance<UtenlandsoppholdVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                UtenlandsoppholdVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                    .slåSammenLikePerioder()
            } else {
                UtenlandsoppholdVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.opplysningspliktVilkår(): OpplysningspliktVilkår {
    return map { it.opplysningspliktVilkår() }
        .filterIsInstance<OpplysningspliktVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                    .slåSammenLikePerioder()
            } else {
                OpplysningspliktVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.pensjonsVilkår(): PensjonsVilkår {
    return flatMap { vedtak ->
        vedtak.pensjonsVilkår().fold(
            {
                emptyList()
            },
            {
                when (it) {
                    PensjonsVilkår.IkkeVurdert -> emptyList()
                    is PensjonsVilkår.Vurdert -> it.vurderingsperioder
                }
            },
        )
    }.let {
        if (it.isNotEmpty()) {
            PensjonsVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                .slåSammenLikePerioder()
        } else {
            PensjonsVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.familiegjenforeningvilkår(): FamiliegjenforeningVilkår {
    return flatMap { vedtak ->
        vedtak.familiegjenforeningvilkår().fold(
            {
                emptyList()
            },
            {
                when (it) {
                    FamiliegjenforeningVilkår.IkkeVurdert -> emptyList()
                    is FamiliegjenforeningVilkår.Vurdert -> it.vurderingsperioder
                }
            },
        )
    }.let {
        if (it.isNotEmpty()) {
            FamiliegjenforeningVilkår.Vurdert.createFromVilkårsvurderinger(NonEmptyList.fromListUnsafe(it))
                .slåSammenLikePerioder()
        } else {
            FamiliegjenforeningVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.flyktningVilkår(): FlyktningVilkår {
    return flatMap { vedtak ->
        vedtak.flyktningVilkår().fold(
            {
                emptyList()
            },
            {
                when (it) {
                    FlyktningVilkår.IkkeVurdert -> emptyList()
                    is FlyktningVilkår.Vurdert -> it.vurderingsperioder
                }
            },
        )
    }.let {
        if (it.isNotEmpty()) {
            FlyktningVilkår.Vurdert.create(NonEmptyList.fromListUnsafe(it)).slåSammenLikePerioder()
        } else {
            FlyktningVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakSomKanRevurderes.VedtakPåTidslinje>.fastOppholdINorgeVilkår(): FastOppholdINorgeVilkår {
    return map { it.fastOppholdVilkår() }
        .filterIsInstance<FastOppholdINorgeVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let { vurderingsperioder ->
            if (vurderingsperioder.isNotEmpty()) {
                FastOppholdINorgeVilkår.Vurdert.tryCreate(NonEmptyList.fromListUnsafe(vurderingsperioder))
                    .getOrHandle { throw IllegalArgumentException(it.toString()) }
                    .slåSammenLikePerioder()
            } else {
                FastOppholdINorgeVilkår.IkkeVurdert
            }
        }
}
