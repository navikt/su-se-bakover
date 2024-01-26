package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.slåSammenPeriodeOgBosituasjon
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag.Companion.slåSammenPeriodeOgFradrag
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.uføre.domain.UføreVilkår
import java.time.Clock
import java.time.LocalDate

/**
 * Gjeldende vedtaksdata, krympet til ønsket periode.
 */
data class GjeldendeVedtaksdata(
    // TODO Finne et bedre navn. Dette er ikke all vedtaksdata, men kun det som kan Revurderes og Reguleres
    // Perioden vi ønsker å finne gjeldende vedtaksdata for. Det er ikke gitt at man har kontinuerlig ytelse innenfor denne perioden.
    private val periode: Periode,
    private val vedtakListe: NonEmptyList<VedtakSomKanRevurderes>,
    private val clock: Clock,
) {
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering
    val grunnlagsdata: Grunnlagsdata get() = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger.Revurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger

    private val tidslinje: Tidslinje<VedtakPåTidslinje>? = vedtakListe.lagTidslinje().krympTilPeriode(periode)

    private val vedtakPåTidslinje: List<VedtakPåTidslinje> = tidslinje ?: emptyList()

    // TODO istedenfor å bruke constructor + init, burde GjeldendeVedtaksdata ha en tryCreate
    init {
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = vedtakPåTidslinje.flatMap {
                    it.grunnlagsdata.fradragsgrunnlag
                }.slåSammenPeriodeOgFradrag(clock),
                bosituasjon = vedtakPåTidslinje.flatMap {
                    it.grunnlagsdata.bosituasjonSomFullstendig()
                }.slåSammenPeriodeOgBosituasjon(),
            ),
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
                            personligOppmøte = it.personligOppmøteVilkår(),
                            institusjonsopphold = it.institusjonsoppholdVilkår(),
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
                            personligOppmøte = it.personligOppmøteVilkår(),
                        )
                    }

                    else -> {
                        throw IllegalStateException("Kan ikke hente gjeldende vedtaksdata for blanding av uføre og alder.")
                    }
                }
            },
        )
    }

    fun harStans(): Boolean {
        return vedtakPåTidslinje.map { it.originaltVedtak }
            .filterIsInstance<VedtakStansAvYtelse>().isNotEmpty()
    }

    private fun helePeriodenErOpphør(): Boolean {
        return vedtakPåTidslinje.all { it.erOpphør() }
    }

    fun delerAvPeriodenErOpphør(): Boolean {
        return !helePeriodenErOpphør() && vedtakPåTidslinje.any { it.erOpphør() }
    }

    /**
     * Bruk heller [gjeldendeVedtakForMåned]
     * Beholdes inntil vi har fjernet AbstractRevurdering.tilRevurdering
     */
    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? =
        tidslinje?.gjeldendeForDato(dato)?.originaltVedtak

    fun gjeldendeVedtakForMåned(måned: Måned): VedtakSomKanRevurderes? =
        tidslinje?.gjeldendeForMåned(måned)?.originaltVedtak

    @Suppress("MemberVisibilityCanBePrivate")
    fun gjeldendeVedtakMånedsvis(): Map<Måned, VedtakSomKanRevurderes?> {
        return periode.måneder().associateWith { gjeldendeVedtakForMåned(it) }
    }

    fun gjeldendeVedtakMånedsvisMedPotensielleHull(): Map<Måned, VedtakSomKanRevurderes> {
        return gjeldendeVedtakMånedsvis().filterValues { it != null }.mapValues { it.value!! }
    }

    fun tidslinjeForVedtakErSammenhengende(): Boolean {
        return vedtaksperioder().minsteAntallSammenhengendePerioder().count() == 1
    }

    fun harVedtakIHelePerioden(): Boolean {
        return periode.måneder().map { gjeldendeVedtakForMåned(it) }.none { it == null } &&
            tidslinjeForVedtakErSammenhengende() &&
            periode == garantertSammenhengendePeriode()
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

private fun List<VedtakPåTidslinje>.uføreVilkår(): UføreVilkår {
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
            UføreVilkår.Vurdert.fromVurderingsperioder(
                vurderingsperioder = it.toNonEmptyList(),

            )
                .getOrElse { throw IllegalArgumentException("Kunne ikke instansiere ${UføreVilkår.Vurdert::class.simpleName}. Melding: $it") }
                .slåSammenLikePerioder()
        } else {
            UføreVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakPåTidslinje>.lovligoppholdVilkår(): LovligOppholdVilkår {
    return map { it.lovligOppholdVilkår() }
        .filterIsInstance<LovligOppholdVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                LovligOppholdVilkår.Vurdert.createFromVilkårsvurderinger(
                    it.toNonEmptyList(),
                )
                    .slåSammenLikePerioder()
            } else {
                LovligOppholdVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakPåTidslinje>.formueVilkår(): FormueVilkår {
    return map { it.formueVilkår() }
        .filterIsInstance<FormueVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                FormueVilkår.Vurdert.createFromVilkårsvurderinger(
                    it.toNonEmptyList(),
                )
                    .slåSammenLikePerioder()
            } else {
                FormueVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakPåTidslinje>.utenlandsoppholdVilkår(): UtenlandsoppholdVilkår {
    return map { it.utenlandsoppholdVilkår() }
        .filterIsInstance<UtenlandsoppholdVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                UtenlandsoppholdVilkår.Vurdert.createFromVilkårsvurderinger(
                    it.toNonEmptyList(),
                )
                    .slåSammenLikePerioder()
            } else {
                UtenlandsoppholdVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakPåTidslinje>.opplysningspliktVilkår(): OpplysningspliktVilkår {
    return map { it.opplysningspliktVilkår() }
        .filterIsInstance<OpplysningspliktVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
                    it.toNonEmptyList(),
                )
                    .slåSammenLikePerioder()
            } else {
                OpplysningspliktVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakPåTidslinje>.pensjonsVilkår(): PensjonsVilkår {
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
            PensjonsVilkår.Vurdert.createFromVilkårsvurderinger(
                it.toNonEmptyList(),

            )
                .slåSammenLikePerioder()
        } else {
            PensjonsVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakPåTidslinje>.familiegjenforeningvilkår(): FamiliegjenforeningVilkår {
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
            FamiliegjenforeningVilkår.Vurdert.createFromVilkårsvurderinger(
                it.toNonEmptyList(),

            )
                .slåSammenLikePerioder()
        } else {
            FamiliegjenforeningVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakPåTidslinje>.flyktningVilkår(): FlyktningVilkår {
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
            FlyktningVilkår.Vurdert.create(
                it.toNonEmptyList(),

            ).slåSammenLikePerioder()
        } else {
            FlyktningVilkår.IkkeVurdert
        }
    }
}

private fun List<VedtakPåTidslinje>.fastOppholdINorgeVilkår(): FastOppholdINorgeVilkår {
    return map { it.fastOppholdVilkår() }
        .filterIsInstance<FastOppholdINorgeVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let { vurderingsperioder ->
            if (vurderingsperioder.isNotEmpty()) {
                FastOppholdINorgeVilkår.Vurdert.tryCreate(
                    vurderingsperioder.toNonEmptyList(),
                )
                    .getOrElse { throw IllegalArgumentException(it.toString()) }
                    .slåSammenLikePerioder()
            } else {
                FastOppholdINorgeVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakPåTidslinje>.personligOppmøteVilkår(): PersonligOppmøteVilkår {
    return map { it.personligOppmøteVilkår() }
        .filterIsInstance<PersonligOppmøteVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                PersonligOppmøteVilkår.Vurdert(
                    it.toNonEmptyList(),
                ).slåSammenLikePerioder()
            } else {
                PersonligOppmøteVilkår.IkkeVurdert
            }
        }
}

private fun List<VedtakPåTidslinje>.institusjonsoppholdVilkår(): InstitusjonsoppholdVilkår {
    return map { it.vilkårsvurderinger.institusjonsopphold }
        .filterIsInstance<InstitusjonsoppholdVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                InstitusjonsoppholdVilkår.Vurdert.create(
                    it.toNonEmptyList(),
                ).slåSammenLikePerioder()
            } else {
                InstitusjonsoppholdVilkår.IkkeVurdert
            }
        }
}
