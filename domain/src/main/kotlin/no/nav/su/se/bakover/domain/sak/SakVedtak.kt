package no.nav.su.se.bakover.domain.sak

import beregning.domain.Beregning
import beregning.domain.BeregningStrategyFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import vedtak.domain.Vedtak
import java.time.Clock
import java.util.UUID

/**
 * Spesialfunksjon for resending av utbetaling for gjenopptak av ytelse.
 *
 * Merk at denne beregner på nytt basert på gjeldende grunnlagsdata og vilkårsvurderinger.
 * Dersom vi har fått nye satser i mellomtiden, vil disse bli med.
 *
 * @param vedtakId Id til vedtaket som har blitt gjenopptatt.
 *
 * @throws IllegalArgumentException Dersom vedtaket ikke finnes på saken eller ikke er et gjenopptak.
 */
fun Sak.hentBeregningForGjenopptakAvYtelse(
    vedtakId: UUID,
    begrunnelse: String? = null,
    beregningStrategyFactory: BeregningStrategyFactory,
    clock: Clock,
): Beregning {
    val vedtak = hentVedtakForIdEllerKast(vedtakId) as VedtakGjenopptakAvYtelse
    val periode = vedtak.periode
    val gjeldendeGrunnlagsdataForVedtak = hentGjeldendeVedtaksdata(
        periode = periode,
        clock = clock,
    ).getOrNull()!!.grunnlagsdataOgVilkårsvurderinger
    return beregningStrategyFactory.beregn(
        grunnlagsdataOgVilkårsvurderinger = gjeldendeGrunnlagsdataForVedtak,
        begrunnelse = begrunnelse,
        sakstype = this.type,
    )
}

fun Sak.hentVedtakForIdEllerKast(vedtakId: UUID): Vedtak {
    return hentVedtakForId(vedtakId)
        ?: throw IllegalArgumentException("Fant ikke vedtak med id $vedtakId på sak $id")
}

fun Sak.hentVedtakForId(vedtakId: UUID): Vedtak? {
    return this.vedtakListe.find { it.id == vedtakId }
}

/**
 * Henter siste vedtakstidspunkt for en sak. I.e. Vedtak.opprettet.
 */
fun Sak.sisteVedtakstidpunkt(): Tidspunkt? {
    return this.vedtakListe.map { it.opprettet }.maxByOrNull { it } ?: null
}
