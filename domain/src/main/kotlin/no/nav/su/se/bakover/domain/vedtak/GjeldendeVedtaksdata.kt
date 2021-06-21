package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.LocalDate
import java.util.UUID

data class GjeldendeVedtaksdata(
    val periode: Periode,
    private val vedtakListe: NonEmptyList<VedtakSomKanRevurderes>,
) {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger

    private val tidslinje = vedtakListe
        .lagTidslinje(periode)

    private val vedtakPåTidslinje = tidslinje.tidslinje

    private val vilkårsvurderingerFraTidslinje = vedtakPåTidslinje.vilkårsvurderinger()

    // Utleder grunnlagstyper som kan knyttes til vilkår via deres respektive vilkårsvurderinger
    private val uføreGrunnlagOgVilkår = when (val uførevilkår = vilkårsvurderingerFraTidslinje.uføre) {
        Vilkår.Uførhet.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
        is Vilkår.Uførhet.Vurdert -> uførevilkår
    }

    private val formuevilkårOgGrunnlag = when (val formue = vilkårsvurderingerFraTidslinje.formue) {
        Vilkår.Formue.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
        is Vilkår.Formue.Vurdert -> formue
    }

    private val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = vedtakPåTidslinje.flatMap { it.fradrag }.map {
        Grunnlag.Fradragsgrunnlag(id = UUID.randomUUID(), opprettet = Tidspunkt.now(), fradrag = it)
    }

    init {
        grunnlagsdata = Grunnlagsdata(
            fradragsgrunnlag = fradragsgrunnlag,
            bosituasjon = vedtakPåTidslinje.flatMap {
                it.grunnlagsdata.bosituasjon
            },
        )
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreGrunnlagOgVilkår,
            formue = formuevilkårOgGrunnlag,
        )
    }

    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? = tidslinje.gjeldendeForDato(dato)?.originaltVedtak

    fun tidslinjeForVedtakErSammenhengende() = vedtakPåTidslinje
        .zipWithNext { a, b -> a.periode tilstøter b.periode }
        .all { it }
}

private fun List<Vedtak.VedtakPåTidslinje>.vilkårsvurderinger(): Vilkårsvurderinger {
    return Vilkårsvurderinger(
        uføre = Vilkår.Uførhet.Vurdert.tryCreate(
            map { it.vilkårsvurderinger.uføre }
                .filterIsInstance<Vilkår.Uførhet.Vurdert>()
                .flatMap { it.vurderingsperioder }
                .let { Nel.fromListUnsafe(it) },
        ).getOrHandle {
            throw IllegalArgumentException("Kunne ikke instansiere ${Vilkår.Uførhet.Vurdert::class.simpleName}. Melding: $it")
        },
        formue = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
            map { it.vilkårsvurderinger.formue }
                .filterIsInstance<Vilkår.Formue.Vurdert>()
                .flatMap { it.vurderingsperioder }
                .let { Nel.fromListUnsafe(it) },
        ),
    )
}
