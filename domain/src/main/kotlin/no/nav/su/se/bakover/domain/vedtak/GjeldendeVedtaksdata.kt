package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.slåSammenPeriodeOgBosituasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.slåSammenPeriodeOgFradrag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate

data class GjeldendeVedtaksdata(
    // TODO Finne et bedre navn. Dette er ikke all vedtaksdata, men kun det som kan Revurderes og Reguleres
    val periode: Periode,
    private val vedtakListe: NonEmptyList<VedtakSomKanRevurderes>,
    private val clock: Clock,
) {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger.Revurdering // TODO. Kan vi bruke denne til Regulering også, eller skal vi lage en ny ReguleringType

    private val tidslinje: Tidslinje<VedtakSomKanRevurderes.VedtakPåTidslinje> = vedtakListe
        .lagTidslinje(periode)

    private val vedtakPåTidslinje: List<VedtakSomKanRevurderes.VedtakPåTidslinje> = tidslinje.tidslinje

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
                Vilkår.Formue.IkkeVurdert -> vilkår
                is Vilkår.Formue.Vurdert -> vilkår
            }
            is Vilkårsvurderinger.Søknadsbehandling -> when (val vilkår = vilkårsvurderinger.formue) {
                Vilkår.Formue.IkkeVurdert -> vilkår
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
    }

    fun harStans(): Boolean {
        return vedtakPåTidslinje.map { it.originaltVedtak }.filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse>().isNotEmpty()
    }

    fun helePeriodenErOpphør(): Boolean {
        return vedtakPåTidslinje.all { it.erOpphør() }
    }

    fun delerAvPeriodenErOpphør(): Boolean {
        return !helePeriodenErOpphør() && vedtakPåTidslinje.any { it.erOpphør() }
    }

    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? =
        tidslinje.gjeldendeForDato(dato)?.originaltVedtak

    fun tidslinjeForVedtakErSammenhengende(): Boolean = vedtakPåTidslinje
        .zipWithNext { a, b -> a.periode tilstøter b.periode }
        .all { it }

    fun inneholderOpphørsvedtakMedAvkortingUtenlandsopphold(): Boolean {
        return periode.tilMånedsperioder()
            .mapNotNull { gjeldendeVedtakPåDato(it.fraOgMed) }
            .filterIsInstance<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>()
            .any {
                when (it.behandling.avkorting) {
                    is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> {
                        false
                    }
                    AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> {
                        false
                    }
                    is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
                        true
                    }
                    is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                        true
                    }
                    is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> {
                        false
                    }
                }
            }
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
                        .slåSammenVurderingsperioder()
                        .getOrHandle { throw IllegalArgumentException("Kunne ikke slå sammen vurderingsperioder uføre: $it") }
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
                        .slåSammenVurderingsperioder()
                        .getOrHandle { throw IllegalArgumentException("Kunne ikke slå sammen vurderingsperioder formue: $it") }
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
                        .slåSammenVurderingsperioder()
                        .getOrHandle { throw IllegalArgumentException("Kunne ikke slå sammen vurderingsperioder utlandsopphold: $it") }
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
