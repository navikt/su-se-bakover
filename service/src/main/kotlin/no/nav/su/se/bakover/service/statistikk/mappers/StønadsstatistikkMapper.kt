package no.nav.su.se.bakover.service.statistikk.mappers

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.statistikk.Statistikk
import no.nav.su.se.bakover.service.statistikk.stønadsklassifisering
import java.time.Clock
import java.time.LocalDate

class StønadsstatistikkMapper(
    private val clock: Clock,
) {
    fun map(
        vedtak: VedtakSomKanRevurderes.EndringIYtelse,
        aktørId: AktørId,
        ytelseVirkningstidspunkt: LocalDate,
        sak: Sak,
    ): Statistikk.Stønad {
        val nå = Tidspunkt.now(clock)

        return Statistikk.Stønad(
            funksjonellTid = nå,
            tekniskTid = nå,
            stonadstype = Statistikk.Stønad.Stønadstype.SU_UFØR,
            sakId = vedtak.behandling.sakId,
            aktorId = aktørId.toString().toLong(),
            sakstype = vedtakstype(vedtak),
            vedtaksdato = vedtak.opprettet.toLocalDate(zoneIdOslo),
            vedtakstype = vedtakstype(vedtak),
            vedtaksresultat = when (vedtak) {
                is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> Statistikk.Stønad.Vedtaksresultat.GJENOPPTATT
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> Statistikk.Stønad.Vedtaksresultat.INNVILGET
                is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> Statistikk.Stønad.Vedtaksresultat.OPPHØRT
                is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> Statistikk.Stønad.Vedtaksresultat.STANSET
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> Statistikk.Stønad.Vedtaksresultat.REGULERT
            },
            behandlendeEnhetKode = "4815",
            ytelseVirkningstidspunkt = ytelseVirkningstidspunkt,
            gjeldendeStonadVirkningstidspunkt = vedtak.behandling.periode.fraOgMed,
            gjeldendeStonadStopptidspunkt = vedtak.behandling.periode.tilOgMed,
            gjeldendeStonadUtbetalingsstart = vedtak.behandling.periode.fraOgMed,
            gjeldendeStonadUtbetalingsstopp = vedtak.behandling.periode.tilOgMed,
            månedsbeløp = when (vedtak) {
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> mapBeregning(vedtak, vedtak.beregning)
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> mapBeregning(
                    vedtak,
                    vedtak.beregning,
                )

                /**
                 * TODO ai 10.11.2021: Endre når revurdering ikke trenger å opphøre behandlingen fra 'fraDato':en
                 */
                is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> emptyList()

                is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> emptyList()
                is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> mapBeregning(
                    vedtak = vedtak,
                    sak = sak,
                    clock = clock,
                )
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> mapBeregning(vedtak, vedtak.beregning)
            },
            versjon = nå.toEpochMilli(),
            opphorsgrunn = when (vedtak) {
                is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> vedtak.behandling.utledOpphørsgrunner(
                    clock,
                ).joinToString()
                else -> null
            },
            opphorsdato = when (vedtak) {
                is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> vedtak.behandling.utledOpphørsdato(clock)
                else -> null
            },
        )
    }
}

private fun mapBeregning(
    vedtak: VedtakSomKanRevurderes.EndringIYtelse,
    beregning: Beregning,
): List<Statistikk.Stønad.Månedsbeløp> {
    val alleFradrag = beregning.tilFradragPerMåned()
    val månedsberegninger = beregning.getMånedsberegninger().associateBy { it.måned }

    return beregning.periode.måneder().map { måned ->
        Statistikk.Stønad.Månedsbeløp(
            inntekter = alleFradrag[måned]?.map { it ->
                Statistikk.Inntekt(
                    inntektstype = it.fradragstype.toString(),
                    beløp = it.månedsbeløp.toLong(),
                    tilhører = it.tilhører.toString(),
                    erUtenlandsk = it.utenlandskInntekt != null,
                )
            } ?: emptyList(),
            bruttosats = månedsberegninger[måned]?.getSatsbeløp()?.toLong()!!,
            fradragSum = månedsberegninger[måned]?.getSumFradrag()?.toLong()!!,
            måned = måned.toString(),
            nettosats = månedsberegninger[måned]?.getSatsbeløp()?.toLong()!! - månedsberegninger[måned]?.getSumFradrag()?.toLong()!!,
            stonadsklassifisering = stønadsklassifisering(vedtak.behandling, månedsberegninger[måned]!!),
        )
    }
}

fun Beregning?.tilFradragPerMåned(): Map<Måned, List<FradragForMåned>> = this?.let { beregning ->
    beregning.getFradrag()
        .flatMap { FradragFactory.periodiser(it) }
}?.groupBy { it.måned } ?: emptyMap()

private fun mapBeregning(
    vedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse,
    sak: Sak,
    clock: Clock,
): List<Statistikk.Stønad.Månedsbeløp> {
    val beregning: Beregning = vedtak.periode.måneder().firstNotNullOf {
        sak.hentGjeldendeBeregningForEndringIYtelse(it, clock)
    }
    return mapBeregning(vedtak, beregning)
}

private fun vedtakstype(vedtak: VedtakSomKanRevurderes.EndringIYtelse) = when (vedtak) {
    is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> Statistikk.Stønad.Vedtakstype.GJENOPPTAK
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> Statistikk.Stønad.Vedtakstype.REVURDERING
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> Statistikk.Stønad.Vedtakstype.SØKNAD
    is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> Statistikk.Stønad.Vedtakstype.REVURDERING
    is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> Statistikk.Stønad.Vedtakstype.STANS
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> Statistikk.Stønad.Vedtakstype.REGULERING
}

private fun stønadsklassifisering(
    behandling: Behandling,
    månedsberegning: Månedsberegning,
): Statistikk.Stønadsklassifisering {
    val bosituasjon = behandling.grunnlagsdata.bosituasjon.single {
        it.periode inneholder månedsberegning.periode
    }

    return when (bosituasjon) {
        is Grunnlag.Bosituasjon.Fullstendig -> bosituasjon.stønadsklassifisering()
        is Grunnlag.Bosituasjon.Ufullstendig -> throw RuntimeException("Fant ikke stønadsklassifisering for bosituasjon")
    }
}
