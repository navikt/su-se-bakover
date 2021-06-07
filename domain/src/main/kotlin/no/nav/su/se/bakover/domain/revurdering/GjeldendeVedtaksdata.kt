package no.nav.su.se.bakover.domain.revurdering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.domain.vedtak.vilkårsvurderinger
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
    val gjeldendePeriodeTilOriginaltVedtak: Map<Periode, VedtakSomKanRevurderes>

    private val vedtakstidslinje = vedtakListe
        .lagTidslinje(periode)

    private val vilkårsvurderingerFraTidslinje = vedtakstidslinje.vilkårsvurderinger()

    // Utleder grunnlagstyper som kan knyttes til vilkår via deres respektive vilkårsvurderinger
    private val uføreGrunnlagOgVilkår = when (val uførevilkår = vilkårsvurderingerFraTidslinje.uføre) {
        Vilkår.IkkeVurdert.Uførhet -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
        is Vilkår.Vurdert.Uførhet -> Pair(uførevilkår.grunnlag, uførevilkår)
    }

    private val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = vedtakstidslinje.flatMap { it.fradrag }.map {
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
        gjeldendePeriodeTilOriginaltVedtak = vedtakstidslinje.associate { it.periode to it.originaltVedtak }
    }

    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? = gjeldendePeriodeTilOriginaltVedtak
        .filter { dato.between(it.key) }
        .minByOrNull { it.key.fraOgMed }?.value

    fun tidslinjeForVedtakErSammenhengende() = gjeldendePeriodeTilOriginaltVedtak.keys
        .zipWithNext { a, b -> a tilstøter b }
        .all { it }
}
