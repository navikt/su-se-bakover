package no.nav.su.se.bakover.statistikk.stønad

import arrow.core.Either
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.GitCommit
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.statistikk.SchemaValidator
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto.Companion.stønadsklassifisering
import no.nav.su.se.bakover.statistikk.ValidertStatistikkJsonMelding
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToInt

private val log = LoggerFactory.getLogger("StønadsstatistikkMapper.kt")

private val stønadSchema: JsonSchema = SchemaValidator.createSchema("/statistikk/stonad_schema.json")

internal fun StatistikkEvent.Stønadsvedtak.toStønadstatistikkDto(
    aktørId: AktørId,
    ytelseVirkningstidspunkt: LocalDate,
    sak: Sak,
    clock: Clock,
    gitCommit: GitCommit?,
): Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding> {
    return toDto(
        vedtak = this.vedtak,
        aktørId = aktørId,
        ytelseVirkningstidspunkt = ytelseVirkningstidspunkt,
        sak = sak,
        clock = clock,
        gitCommit = gitCommit,
        funksjonellTid = this.vedtak.opprettet,
    ).let {
        serialize(it).let {
            SchemaValidator.validate(it, stønadSchema).map {
                ValidertStatistikkJsonMelding(
                    topic = "supstonad.aapen-su-stonad-statistikk-v1",
                    validertJsonMelding = it,
                )
            }
        }
    }
}

private fun toDto(
    vedtak: VedtakSomKanRevurderes.EndringIYtelse,
    aktørId: AktørId,
    ytelseVirkningstidspunkt: LocalDate,
    sak: Sak,
    clock: Clock,
    gitCommit: GitCommit?,
    funksjonellTid: Tidspunkt,
): StønadstatistikkDto {
    return StønadstatistikkDto(
        funksjonellTid = funksjonellTid,
        tekniskTid = Tidspunkt.now(clock),
        stonadstype = StønadstatistikkDto.Stønadstype.SU_UFØR,
        sakId = vedtak.behandling.sakId,
        aktorId = aktørId.toString().toLong(),
        sakstype = vedtakstype(vedtak),
        vedtaksdato = vedtak.opprettet.toLocalDate(zoneIdOslo),
        vedtakstype = vedtakstype(vedtak),
        vedtaksresultat = when (vedtak) {
            is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> StønadstatistikkDto.Vedtaksresultat.GJENOPPTATT
            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> StønadstatistikkDto.Vedtaksresultat.INNVILGET
            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> StønadstatistikkDto.Vedtaksresultat.INNVILGET
            is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> StønadstatistikkDto.Vedtaksresultat.OPPHØRT
            is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> StønadstatistikkDto.Vedtaksresultat.STANSET
            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> StønadstatistikkDto.Vedtaksresultat.REGULERT
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

            /** TODO ai 10.11.2021: Endre når revurdering ikke trenger å opphøre behandlingen fra 'fraDato':en */
            is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> emptyList()

            is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> emptyList()
            is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> mapBeregning(
                vedtak = vedtak,
                sak = sak,
                clock = clock,
            )

            is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> mapBeregning(vedtak, vedtak.beregning)
        },
        versjon = gitCommit?.value,
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

private fun mapBeregning(
    vedtak: VedtakSomKanRevurderes.EndringIYtelse,
    beregning: Beregning,
): List<StønadstatistikkDto.Månedsbeløp> {
    val alleFradrag = beregning.tilFradragPerMåned()
    val månedsberegninger = beregning.getMånedsberegninger().associateBy { it.måned }

    val månederIVedtakOgBeregning =
        vedtak.periode.måneder().toSet().intersect(beregning.periode.måneder().toSet()).toList()

    return månederIVedtakOgBeregning.map { måned ->
        val fradrag = maxAvForventetInntektOgArbeidsInntekt(alleFradrag[måned]!!)

        val månedsberegning = månedsberegninger[måned]!!
        StønadstatistikkDto.Månedsbeløp(
            inntekter = fradrag.map {
                StønadstatistikkDto.Inntekt(
                    inntektstype = it.fradragstype.toString(),
                    beløp = it.månedsbeløp.toLong(),
                    tilhører = it.tilhører.toString(),
                    erUtenlandsk = it.utenlandskInntekt != null,
                )
            },
            bruttosats = månedsberegning.getSatsbeløp().roundToInt().toLong(),
            fradragSum = månedsberegning.getSumFradrag().toLong(),
            måned = månedsberegning.periode.fraOgMed.toString(),
            nettosats = månedsberegning.getSumYtelse().toLong(),
            stonadsklassifisering = stønadsklassifisering(vedtak.behandling, månedsberegning),
        )
    }
}

private fun maxAvForventetInntektOgArbeidsInntekt(fradragForMåned: List<FradragForMåned>): List<FradragForMåned> {
    val (IEUogArbeidsInntekt, rest) = fradragForMåned.partition {
        listOf(
            Fradragstype.ForventetInntekt,
            Fradragstype.Arbeidsinntekt,
        ).contains(it.fradragstype)
    }
    return listOfNotNull(IEUogArbeidsInntekt.maxByOrNull { it.månedsbeløp }) + rest
}

private fun Beregning?.tilFradragPerMåned(): Map<Måned, List<FradragForMåned>> = this?.let { beregning ->
    beregning.getFradrag()
        .flatMap { FradragFactory.periodiser(it) }
}?.groupBy { it.måned } ?: emptyMap()

private fun mapBeregning(
    vedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse,
    sak: Sak,
    clock: Clock,
): List<StønadstatistikkDto.Månedsbeløp> {
    val beregningForMåned = vedtak.periode.måneder()
        .toList()
        .flatMap {
            val beregning = sak.hentGjeldendeBeregningForEndringIYtelsePåDato(it, clock)!!
            mapBeregning(vedtak, beregning)
        }
    val gjeldendeBeregningForMåned = beregningForMåned.associateBy { it.måned }

    return vedtak.periode.måneder().map { gjeldendeBeregningForMåned[it.fraOgMed.toString()]!! }
}

private fun vedtakstype(vedtak: VedtakSomKanRevurderes.EndringIYtelse) = when (vedtak) {
    is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse -> StønadstatistikkDto.Vedtakstype.GJENOPPTAK
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> StønadstatistikkDto.Vedtakstype.REVURDERING
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> StønadstatistikkDto.Vedtakstype.SØKNAD
    is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> StønadstatistikkDto.Vedtakstype.REVURDERING
    is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse -> StønadstatistikkDto.Vedtakstype.STANS
    is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> StønadstatistikkDto.Vedtakstype.REGULERING
}

private fun stønadsklassifisering(
    behandling: Behandling,
    månedsberegning: Månedsberegning,
): StønadsklassifiseringDto {
    val bosituasjon = behandling.grunnlagsdata.bosituasjon.single {
        it.periode inneholder månedsberegning.periode
    }

    return when (bosituasjon) {
        is Grunnlag.Bosituasjon.Fullstendig -> bosituasjon.stønadsklassifisering()
        is Grunnlag.Bosituasjon.Ufullstendig -> StønadsklassifiseringDto.UFULLSTENDIG_GRUNNLAG.also {
            log.error("Kunne ikke avgjøre stønadsklassifisering ved utsending av statistikk, siden grunnlaget var ufullstendig. Behandling id: ${behandling.id}")
        }
    }
}
