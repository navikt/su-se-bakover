package no.nav.su.se.bakover.service.statistikk.mappers

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.statistikk.Statistikk
import no.nav.su.se.bakover.service.statistikk.stønadsklassifisering
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToInt

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
): List<Statistikk.Stønad.Månedsbeløp> =
    beregning.getMånedsberegninger().map {
        tilMånedsbeløp(it, vedtak)
    }

private fun mapBeregning(
    vedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse,
    sak: Sak,
    clock: Clock,
): List<Statistikk.Stønad.Månedsbeløp> =
    vedtak.periode.måneder().map {
        sak.hentGjeldendeMånedsberegningForMåned(it, clock)!!
    }.map {
        tilMånedsbeløp(it, vedtak)
    }

private fun tilMånedsbeløp(
    månedsberegning: Månedsberegning,
    vedtak: VedtakSomKanRevurderes.EndringIYtelse,
) = Statistikk.Stønad.Månedsbeløp(
    måned = månedsberegning.periode.fraOgMed.toString(),
    stonadsklassifisering = stønadsklassifisering(vedtak.behandling, månedsberegning),
    bruttosats = månedsberegning.getSatsbeløp().roundToInt().toLong(),
    nettosats = månedsberegning.getSumYtelse().toLong(),
    inntekter = månedsberegning.getFradrag().map { fradrag ->
        Statistikk.Inntekt(
            inntektstype = fradrag.fradragstype.toString(),
            beløp = fradrag.månedsbeløp.toLong(),
        )
    },
    fradragSum = månedsberegning.getSumFradrag().toLong(),
)

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
