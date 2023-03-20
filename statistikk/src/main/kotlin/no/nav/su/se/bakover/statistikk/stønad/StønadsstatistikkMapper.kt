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
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.statistikk.SchemaValidator
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto.Companion.stønadsklassifisering
import no.nav.su.se.bakover.statistikk.ValidertStatistikkJsonMelding
import org.slf4j.LoggerFactory
import java.time.Clock
import kotlin.math.roundToInt

private val log = LoggerFactory.getLogger("StønadsstatistikkMapper.kt")

private val stønadSchema: JsonSchema = SchemaValidator.createSchema("/statistikk/stonad_schema.json")

internal fun StatistikkEvent.Stønadsvedtak.toStønadstatistikkDto(
    aktørId: AktørId,
    hentSak: () -> Sak,
    clock: Clock,
    gitCommit: GitCommit?,
): Either<Set<ValidationMessage>, ValidertStatistikkJsonMelding> {
    return toDto(
        vedtak = this.vedtak,
        aktørId = aktørId,
        hentSak = hentSak,
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
    vedtak: VedtakSomKanRevurderes,
    aktørId: AktørId,
    hentSak: () -> Sak,
    clock: Clock,
    gitCommit: GitCommit?,
    funksjonellTid: Tidspunkt,
): StønadstatistikkDto {
    val sak = hentSak()
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
            is VedtakGjenopptakAvYtelse -> StønadstatistikkDto.Vedtaksresultat.GJENOPPTATT
            is VedtakInnvilgetRevurdering -> StønadstatistikkDto.Vedtaksresultat.INNVILGET
            is VedtakInnvilgetSøknadsbehandling -> StønadstatistikkDto.Vedtaksresultat.INNVILGET
            is Opphørsvedtak -> StønadstatistikkDto.Vedtaksresultat.OPPHØRT

            is VedtakStansAvYtelse -> StønadstatistikkDto.Vedtaksresultat.STANSET
            is VedtakInnvilgetRegulering -> StønadstatistikkDto.Vedtaksresultat.REGULERT
        },
        behandlendeEnhetKode = "4815",
        ytelseVirkningstidspunkt = sak.førsteYtelsesdato!!,
        gjeldendeStonadVirkningstidspunkt = vedtak.behandling.periode.fraOgMed,
        gjeldendeStonadStopptidspunkt = vedtak.behandling.periode.tilOgMed,
        gjeldendeStonadUtbetalingsstart = vedtak.behandling.periode.fraOgMed,
        gjeldendeStonadUtbetalingsstopp = vedtak.behandling.periode.tilOgMed,
        månedsbeløp = when (vedtak) {
            is VedtakInnvilgetRevurdering -> mapBeregning(vedtak, vedtak.beregning)
            is VedtakInnvilgetSøknadsbehandling -> mapBeregning(
                vedtak,
                vedtak.beregning,
            )

            /** TODO ai 10.11.2021: Endre når revurdering ikke trenger å opphøre behandlingen fra 'fraDato':en */
            // TODO jah: Vi bør ta en avklaring med stønadsstatistikk-teamet hva slags statistikk de forventer for opphørte måneder med avkortingsvarsel (vi sender ikke utbetalingslinjer for disse månedene).
            is Opphørsvedtak -> emptyList()

            is VedtakStansAvYtelse -> emptyList()
            is VedtakGjenopptakAvYtelse -> mapBeregning(
                vedtak = vedtak,
                hentSak = { sak },
                clock = clock,
            )

            is VedtakInnvilgetRegulering -> mapBeregning(vedtak, vedtak.beregning)
        },
        versjon = gitCommit?.value,
        opphorsgrunn = when (vedtak) {
            is Opphørsvedtak -> vedtak.behandling.utledOpphørsgrunner(
                clock,
            ).joinToString()

            else -> null
        },
        opphorsdato = when (vedtak) {
            is Opphørsvedtak -> vedtak.behandling.utledOpphørsdato(clock)
            else -> null
        },
    )
}

private fun mapBeregning(
    vedtak: VedtakEndringIYtelse,
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
    vedtak: VedtakGjenopptakAvYtelse,
    hentSak: () -> Sak,
    clock: Clock,
): List<StønadstatistikkDto.Månedsbeløp> {
    val beregningForMåned = vedtak.periode.måneder()
        .toList()
        .flatMap {
            val beregning = hentSak().hentGjeldendeBeregningForEndringIYtelsePåDato(it, clock)!!
            mapBeregning(vedtak, beregning)
        }
    val gjeldendeBeregningForMåned = beregningForMåned.associateBy { it.måned }

    return vedtak.periode.måneder().map { gjeldendeBeregningForMåned[it.fraOgMed.toString()]!! }
}

private fun vedtakstype(vedtak: VedtakSomKanRevurderes) = when (vedtak) {
    is VedtakGjenopptakAvYtelse -> StønadstatistikkDto.Vedtakstype.GJENOPPTAK
    is VedtakInnvilgetSøknadsbehandling -> StønadstatistikkDto.Vedtakstype.SØKNAD
    is VedtakInnvilgetRevurdering,
    is Opphørsvedtak,
    -> StønadstatistikkDto.Vedtakstype.REVURDERING

    is VedtakStansAvYtelse -> StønadstatistikkDto.Vedtakstype.STANS
    is VedtakInnvilgetRegulering -> StønadstatistikkDto.Vedtakstype.REGULERING
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
