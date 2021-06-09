package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
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
        Vilkår.IkkeVurdert.Uførhet -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
        is Vilkår.Vurdert.Uførhet -> Pair(uførevilkår.grunnlag, uførevilkår)
    }

    private val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = vedtakPåTidslinje.flatMap { it.fradrag }.map {
        Grunnlag.Fradragsgrunnlag(id = UUID.randomUUID(), opprettet = Tidspunkt.now(), fradrag = it)
    }

    init {
        grunnlagsdata = Grunnlagsdata(
            uføregrunnlag = uføreGrunnlagOgVilkår.first,
            fradragsgrunnlag = fradragsgrunnlag,
        )
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreGrunnlagOgVilkår.second,
        )
    }

    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? = tidslinje.gjeldendeForDato(dato)?.originaltVedtak

    fun tidslinjeForVedtakErSammenhengende() = vedtakPåTidslinje
        .zipWithNext { a, b -> a.periode tilstøter b.periode }
        .all { it }
}
