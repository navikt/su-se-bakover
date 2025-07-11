package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.IkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.SlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import vedtak.domain.Stønadsvedtak
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.slåSammenPeriodeOgBosituasjon
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.inntekt.domain.grunnlag.slåSammen
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.vurderinger.domain.Grunnlagsdata
import java.time.Clock
import java.time.LocalDate

/**
 * Gjeldende vedtaksdata, krympet til ønsket periode.
 */
data class GjeldendeVedtaksdata(
    // TODO Finne et bedre navn. Dette er ikke all vedtaksdata, men kun det som kan Revurderes og Reguleres
    // Perioden vi ønsker å finne gjeldende vedtaksdata for. Det er ikke gitt at man har kontinuerlig ytelse innenfor denne perioden.
    private val periode: Periode,
    private val vedtakListe: NonEmptyList<Stønadsvedtak>,
    private val clock: Clock,
) {
    val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering
    val grunnlagsdata: Grunnlagsdata get() = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata
    val vilkårsvurderinger: VilkårsvurderingerRevurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger

    private val tidslinje: Tidslinje<VedtakPåTidslinje>? = vedtakListe.lagTidslinje().krympTilPeriode(periode)

    private val vedtakPåTidslinje: List<VedtakPåTidslinje> = tidslinje ?: emptyList()

    /** Periodene til vedtakene. Sortert uten overlapp. Kan inneholde hull og trenger ikke være slått sammen. */
    val vedtaksperioder = IkkeOverlappendePerioder.create(vedtakPåTidslinje.map { it.periode })

    // TODO istedenfor å bruke constructor + init, burde GjeldendeVedtaksdata ha en tryCreate
    init {
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = vedtakPåTidslinje.flatMap {
                    it.grunnlagsdata.fradragsgrunnlag
                }.slåSammen(clock),
                bosituasjon = vedtakPåTidslinje.flatMap {
                    it.grunnlagsdata.bosituasjonSomFullstendig()
                }.slåSammenPeriodeOgBosituasjon(),
            ),
            vilkårsvurderinger = vedtakPåTidslinje.let {
                // TODO("vilkårsvurdering_alder mulig vi må/bør gjøre dette på en annen måte")
                when {
                    it.erUføre() -> VilkårsvurderingerRevurdering.Uføre(
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

                    it.erAlder() -> VilkårsvurderingerRevurdering.Alder(
                        lovligOpphold = it.lovligoppholdVilkår(),
                        formue = it.formueVilkår(),
                        utenlandsopphold = it.utenlandsoppholdVilkår(),
                        opplysningsplikt = it.opplysningspliktVilkår(),
                        pensjon = it.pensjonsVilkår(),
                        familiegjenforening = it.familiegjenforeningvilkår(),
                        fastOpphold = it.fastOppholdINorgeVilkår(),
                        personligOppmøte = it.personligOppmøteVilkår(),
                        institusjonsopphold = it.institusjonsoppholdVilkår(),
                    )

                    else -> throw IllegalStateException("Kan ikke hente gjeldende vedtaksdata for blanding av uføre og alder.")
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

    fun opphørtePerioderSlåttSammen(): SlåttSammenIkkeOverlappendePerioder =
        vedtakPåTidslinje.filter { it.erOpphør() }.map { it.periode }.minsteAntallSammenhengendePerioder()

    /**
     * Bruk heller [gjeldendeVedtakForMåned]
     * Beholdes inntil vi har fjernet AbstractRevurdering.tilRevurdering
     */
    fun gjeldendeVedtakPåDato(dato: LocalDate): Stønadsvedtak? =
        tidslinje?.gjeldendeForDato(dato)?.originaltVedtak

    fun gjeldendeVedtakForMåned(måned: Måned): Stønadsvedtak? =
        tidslinje?.gjeldendeForMåned(måned)?.originaltVedtak

    @Suppress("MemberVisibilityCanBePrivate")
    fun gjeldendeVedtakMånedsvis(): Map<Måned, Stønadsvedtak?> {
        return periode.måneder().associateWith { gjeldendeVedtakForMåned(it) }
    }

    fun gjeldendeVedtakMånedsvisMedPotensielleHull(): Map<Måned, Stønadsvedtak> {
        return gjeldendeVedtakMånedsvis().filterValues { it != null }.mapValues { it.value!! }
    }

    fun tidslinjeForVedtakErSammenhengende(): Boolean {
        return vedtaksperioder.minsteAntallSammenhengendePerioder().count() == 1
    }

    fun harVedtakIHelePerioden(): Boolean {
        return periode.måneder().map { gjeldendeVedtakForMåned(it) }.none { it == null } &&
            tidslinjeForVedtakErSammenhengende() &&
            periode == garantertSammenhengendePeriode()
    }

    /**
     * Returnerer en periode hvor det garanteres at tidslinjen er kontinuerlig.
     * @throws IllegalStateException dersom tidslinjen ikke er kontinuerlig.
     */
    fun garantertSammenhengendePeriode(): Periode {
        return vedtaksperioder.minsteAntallSammenhengendePerioder().singleOrNull()
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
            UføreVilkår.Vurdert.tryCreate(
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
