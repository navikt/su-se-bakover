package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
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
    val periode: Periode,
    private val vedtakListe: NonEmptyList<VedtakSomKanRevurderes>,
    private val clock: Clock,
) {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger.Revurdering

    private val tidslinje: Tidslinje<Vedtak.VedtakPåTidslinje> = vedtakListe
        .lagTidslinje(periode)

    private val vedtakPåTidslinje: List<Vedtak.VedtakPåTidslinje> = tidslinje.tidslinje

    private val vilkårsvurderingerFraTidslinje: Vilkårsvurderinger = vedtakPåTidslinje.vilkårsvurderinger()

    // Utleder grunnlagstyper som kan knyttes til vilkår via deres respektive vilkårsvurderinger
    private val uføreGrunnlagOgVilkår: Vilkår.Uførhet.Vurdert =
        when (val vilkårsvurderinger = vilkårsvurderingerFraTidslinje) {
            is Vilkårsvurderinger.Revurdering -> when (val vilkår = vilkårsvurderinger.uføre) {
                Vilkår.Uførhet.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
                is Vilkår.Uførhet.Vurdert -> vilkår
            }
            is Vilkårsvurderinger.Søknadsbehandling -> when (val vilkår = vilkårsvurderinger.uføre) {
                Vilkår.Uførhet.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
                is Vilkår.Uførhet.Vurdert -> vilkår
            }
        }

    private val formuevilkårOgGrunnlag: Vilkår.Formue.Vurdert =
        when (val vilkårsvurderinger = vilkårsvurderingerFraTidslinje) {
            is Vilkårsvurderinger.Revurdering -> when (val vilkår = vilkårsvurderinger.formue) {
                Vilkår.Formue.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
                is Vilkår.Formue.Vurdert -> vilkår
            }
            is Vilkårsvurderinger.Søknadsbehandling -> when (val vilkår = vilkårsvurderinger.formue) {
                Vilkår.Formue.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
                is Vilkår.Formue.Vurdert -> vilkår
            }
        }

    private val utlandsoppholdvilkårOgGrunnlag: UtenlandsoppholdVilkår.Vurdert =
        when (val vilkårsvurderinger = vilkårsvurderingerFraTidslinje) {
            is Vilkårsvurderinger.Revurdering -> when (val vilkår = vilkårsvurderinger.utenlandsopphold) {
                UtenlandsoppholdVilkår.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
                is UtenlandsoppholdVilkår.Vurdert -> vilkår
            }
            is Vilkårsvurderinger.Søknadsbehandling -> when (val vilkår = vilkårsvurderinger.utenlandsopphold) {
                UtenlandsoppholdVilkår.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
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
            uføre = uføreGrunnlagOgVilkår.slåSammenVurderingsperioder()
                .getOrHandle { throw IllegalArgumentException("Kunne ikke slå sammen vurderingsperioder uføre: $it") },
            formue = formuevilkårOgGrunnlag.slåSammenVurderingsperioder()
                .getOrHandle { throw IllegalArgumentException("Kunne ikke slå sammen vurderingsperioder formue: $it") },
            utenlandsopphold = utlandsoppholdvilkårOgGrunnlag.slåSammenVurderingsperioder()
                .getOrHandle { throw IllegalArgumentException("Kunne ikke slå sammen vurderingsperioder utlandsopphold: $it") },
        )
    }

    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? =
        tidslinje.gjeldendeForDato(dato)?.originaltVedtak

    fun tidslinjeForVedtakErSammenhengende(): Boolean = vedtakPåTidslinje
        .zipWithNext { a, b -> a.periode tilstøter b.periode }
        .all { it }
}

private fun List<Vedtak.VedtakPåTidslinje>.vilkårsvurderinger(): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = Vilkår.Uførhet.Vurdert.tryCreate(
            this.map { it.vilkårsvurderinger.uføreVilkår() }
                .filterIsInstance<Vilkår.Uførhet.Vurdert>()
                .flatMap { it.vurderingsperioder }
                .let { Nel.fromListUnsafe(it) },
        ).getOrHandle {
            throw IllegalArgumentException("Kunne ikke instansiere ${Vilkår.Uførhet.Vurdert::class.simpleName}. Melding: $it")
        },
        formue = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
            this.map { it.vilkårsvurderinger.formueVilkår() }
                .filterIsInstance<Vilkår.Formue.Vurdert>()
                .flatMap { it.vurderingsperioder }
                .let { Nel.fromListUnsafe(it) },
        ),
        utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.createFromVilkårsvurderinger(
            this.map { it.vilkårsvurderinger.utenlandsoppholdVilkår() }
                .filterIsInstance<UtenlandsoppholdVilkår.Vurdert>()
                .flatMap { it.vurderingsperioder }
                .let { Nel.fromListUnsafe(it) }
        )
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
