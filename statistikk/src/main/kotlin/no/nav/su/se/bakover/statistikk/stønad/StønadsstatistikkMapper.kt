package no.nav.su.se.bakover.statistikk.stønad

import arrow.core.Either
import behandling.domain.Stønadsbehandling
import beregning.domain.Beregning
import beregning.domain.Månedsberegning
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.statistikk.SchemaValidator
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto
import no.nav.su.se.bakover.statistikk.StønadsklassifiseringDto.Companion.stønadsklassifisering
import no.nav.su.se.bakover.statistikk.ValidertStatistikkJsonMelding
import org.slf4j.LoggerFactory
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.Fradragstype
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
        stonadstype = when (vedtak.sakinfo().type) {
            Sakstype.ALDER -> StønadstatistikkDto.Stønadstype.SU_ALDER
            Sakstype.UFØRE -> StønadstatistikkDto.Stønadstype.SU_UFØR
        },
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
            else -> throw IllegalStateException("Ikke tatt høyde for ${vedtak::class.simpleName} ved generering av statistikk")
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
            is Opphørsvedtak -> emptyList()

            is VedtakStansAvYtelse -> emptyList()
            is VedtakGjenopptakAvYtelse -> mapBeregning(
                vedtak = vedtak,
                hentSak = { sak },
                clock = clock,
            )

            is VedtakInnvilgetRegulering -> mapBeregning(vedtak, vedtak.beregning)
            else -> throw IllegalStateException("Ikke tatt høyde for ${vedtak::class.simpleName} ved generering av statistikk")
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
        flyktningsstatus = when (sak.type) {
            Sakstype.ALDER -> null
            Sakstype.UFØRE -> "FLYKTNING"
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
        val fradrag = alleFradrag[måned]?.let { maxAvForventetInntektOgArbeidsInntekt(it) } ?: emptyList()

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
            val beregning = hentSak().hentGjeldendeBeregningForEndringIYtelseForMåned(it, clock)!!
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
    else -> throw IllegalStateException("Ikke tatt høyde for ${vedtak::class.simpleName} ved generering av statistikk")
}

private fun stønadsklassifisering(
    behandling: Stønadsbehandling,
    månedsberegning: Månedsberegning,
): StønadsklassifiseringDto {
    val bosituasjon = behandling.grunnlagsdata.bosituasjon.single {
        it.periode inneholder månedsberegning.periode
    }

    return when (bosituasjon) {
        is Bosituasjon.Fullstendig -> bosituasjon.stønadsklassifisering()
        is Bosituasjon.Ufullstendig -> StønadsklassifiseringDto.UFULLSTENDIG_GRUNNLAG.also {
            log.error("Kunne ikke avgjøre stønadsklassifisering ved utsending av statistikk, siden grunnlaget var ufullstendig. Behandling id: ${behandling.id}")
        }
    }
}
