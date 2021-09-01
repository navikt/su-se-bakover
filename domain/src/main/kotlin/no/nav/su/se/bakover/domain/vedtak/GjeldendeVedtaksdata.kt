package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
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
    val vilkårsvurderinger: Vilkårsvurderinger

    private val tidslinje: Tidslinje<Vedtak.VedtakPåTidslinje> = vedtakListe
        .lagTidslinje(periode, clock)

    private val vedtakPåTidslinje: List<Vedtak.VedtakPåTidslinje> = tidslinje.tidslinje

    private val vilkårsvurderingerFraTidslinje: Vilkårsvurderinger = vedtakPåTidslinje.vilkårsvurderinger()

    // Utleder grunnlagstyper som kan knyttes til vilkår via deres respektive vilkårsvurderinger
    private val uføreGrunnlagOgVilkår: Vilkår.Uførhet.Vurdert =
        when (val uførevilkår = vilkårsvurderingerFraTidslinje.uføre) {
            Vilkår.Uførhet.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
            is Vilkår.Uførhet.Vurdert -> uførevilkår
        }

    private val formuevilkårOgGrunnlag: Vilkår.Formue.Vurdert =
        when (val formue = vilkårsvurderingerFraTidslinje.formue) {
            Vilkår.Formue.IkkeVurdert -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
            is Vilkår.Formue.Vurdert -> formue
        }

    // TODO istedenfor å bruke constructor + init, burde GjeldendeVedtaksdata ha en tryCreate
    init {
        grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = vedtakPåTidslinje.flatMap {
                it.grunnlagsdata.fradragsgrunnlag
            },
            bosituasjon = vedtakPåTidslinje.flatMap {
                it.grunnlagsdata.bosituasjon
            },
        )
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreGrunnlagOgVilkår,
            formue = formuevilkårOgGrunnlag,
        )
    }

    fun gjeldendeVedtakPåDato(dato: LocalDate): VedtakSomKanRevurderes? =
        tidslinje.gjeldendeForDato(dato)?.originaltVedtak

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
