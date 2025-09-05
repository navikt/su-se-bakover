package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import arrow.core.toNonEmptyListOrNull
import behandling.domain.Stønadsbehandling
import beregning.domain.Beregning
import beregning.domain.Månedsberegning
import no.nav.su.se.bakover.common.domain.JaNei
import no.nav.su.se.bakover.common.domain.extensions.hasOneElement
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkRepo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening
import no.nav.su.se.bakover.domain.vilkår.hentUføregrunnlag
import org.slf4j.LoggerFactory
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadsklassifiseringDto.Companion.stønadsklassifisering
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.vurderinger.domain.VilkårEksistererIkke
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

interface StønadStatistikkJobService {
    fun lagMånedligStønadstatistikk(clock: Clock)
}

class StønadStatistikkJobServiceImpl(
    private val stønadStatistikkRepo: StønadStatistikkRepo,
    private val vedtakRepo: VedtakRepo,
) : StønadStatistikkJobService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagMånedligStønadstatistikk(clock: Clock) {
        val måned = YearMonth.now(clock).minusMonths(1)
        val harKjørt = stønadStatistikkRepo.hentMånedStatistikk(måned).isNotEmpty()
        if (!harKjørt) {
            lagMånedligStønadstatistikk(clock, måned)
        }
    }

    private fun lagMånedligStønadstatistikk(
        clock: Clock,
        måned: YearMonth,
    ) {
        val alleVedtak = vedtakRepo.hentVedtakForMåned(Måned.fra(måned))
        alleVedtak.groupBy { it.behandling.sakId }.forEach {
            val siste = it.value.maxBy { it.opprettet }
            val behandling = siste.behandling as Stønadsbehandling
            val sak = behandling.sakinfo()

            val stønadstatistikk = StønadstatistikkMåned(
                id = UUID.randomUUID(),
                måned = måned,
                funksjonellTid = siste.opprettet,
                tekniskTid = Tidspunkt.now(clock),
                sakId = sak.sakId,
                stonadstype = when (sak.type) {
                    Sakstype.ALDER -> StønadstatistikkDto.Stønadstype.SU_ALDER
                    Sakstype.UFØRE -> StønadstatistikkDto.Stønadstype.SU_UFØR
                },
                personnummer = sak.fnr,
                personNummerEps = behandling.grunnlagsdata.eps.hentEktefelleHvisFinnes(),
                vedtaksdato = siste.opprettet.toLocalDate(zoneIdOslo),
                vedtakstype = when (siste) {
                    is VedtakGjenopptakAvYtelse -> StønadstatistikkDto.Vedtakstype.GJENOPPTAK
                    is VedtakInnvilgetSøknadsbehandling -> StønadstatistikkDto.Vedtakstype.SØKNAD
                    is VedtakInnvilgetRevurdering,
                    is Opphørsvedtak,
                    -> StønadstatistikkDto.Vedtakstype.REVURDERING

                    is VedtakStansAvYtelse -> StønadstatistikkDto.Vedtakstype.STANS
                    is VedtakInnvilgetRegulering -> StønadstatistikkDto.Vedtakstype.REGULERING
                    else -> throw IllegalStateException("Ikke tatt høyde for ${siste::class.simpleName} ved generering av statistikk")
                },
                vedtaksresultat = when (siste) {
                    is VedtakGjenopptakAvYtelse -> StønadstatistikkDto.Vedtaksresultat.GJENOPPTATT
                    is VedtakInnvilgetRevurdering -> StønadstatistikkDto.Vedtaksresultat.INNVILGET
                    is VedtakInnvilgetSøknadsbehandling -> StønadstatistikkDto.Vedtaksresultat.INNVILGET
                    is Opphørsvedtak -> StønadstatistikkDto.Vedtaksresultat.OPPHØRT
                    is VedtakStansAvYtelse -> StønadstatistikkDto.Vedtaksresultat.STANSET
                    is VedtakInnvilgetRegulering -> StønadstatistikkDto.Vedtaksresultat.REGULERT
                    else -> throw IllegalStateException("Ikke tatt høyde for ${siste::class.simpleName} ved generering av statistikk")
                },
                vedtakFraOgMed = behandling.periode.fraOgMed,
                vedtakTilOgMed = behandling.periode.tilOgMed,
                opphorsgrunn = when (siste) {
                    is Opphørsvedtak -> siste.behandling.utledOpphørsgrunner(
                        clock,
                    ).joinToString()

                    else -> null
                },
                opphorsdato = when (siste) {
                    is Opphørsvedtak -> siste.behandling.utledOpphørsdato(clock)
                    else -> null
                },
                harUtenlandsOpphold = vilkarVurdert(behandling.vilkårsvurderinger.utenlandsopphold),
                harFamiliegjenforening = vilkarVurdertHvisEksisterer(behandling.vilkårsvurderinger.familiegjenforening()),
                flyktningsstatus = vilkarVurdertHvisEksisterer(behandling.vilkårsvurderinger.flyktningVilkår()),
                årsakStans = when (siste) {
                    is VedtakStansAvYtelse -> siste.behandling.revurderingsårsak.årsak.name
                    else -> null
                },
                behandlendeEnhetKode = "4815",
                månedsbeløp = when (siste) {
                    is VedtakInnvilgetRevurdering -> mapBeregning(siste, siste.beregning)
                    is VedtakInnvilgetSøknadsbehandling -> mapBeregning(siste, siste.beregning)
                    is VedtakInnvilgetRegulering -> mapBeregning(siste, siste.beregning)
                    is VedtakGjenopptakAvYtelse -> {
                        val beregningSomGjenopptas = GjeldendeVedtaksdata(
                            periode = Måned.fra(måned),
                            vedtakListe = alleVedtak.filterIsInstance<VedtakSomKanRevurderes>()
                                .filter { it.beregning != null }.toNonEmptyListOrNull()
                                ?: throw IllegalStateException("Mangler vedtak med beregning i periode som skal ha blitt gjenopptatt"),
                            clock = clock,
                        ).gjeldendeVedtakForMåned(Måned.fra(måned))?.beregning
                            ?: throw IllegalStateException("Mangler vedtak med beregning i periode som skal ha blitt gjenopptatt")

                        mapBeregning(siste, beregningSomGjenopptas)
                    }
                    is VedtakOpphørMedUtbetaling -> {
                        throw IllegalStateException("Har opphørsvedtak hvor månedsbeløp ikke blir håndtert")
                    }

                    is Opphørsvedtak,
                    is VedtakStansAvYtelse,
                    -> emptyList()

                    else -> throw IllegalStateException("Ikke tatt høyde for ${siste::class.simpleName} ved generering av statistikk")
                }.singleOrNull {
                    YearMonth.from(LocalDate.parse(it.måned, DateTimeFormatter.ISO_DATE)) == måned
                },
            )
            stønadStatistikkRepo.lagreMånedStatistikk(stønadstatistikk)
        }
    }

    private fun List<Fnr>.hentEktefelleHvisFinnes(): Fnr? {
        return if (this.hasOneElement()) {
            this.first()
        } else {
            null
        }
    }

    private fun vilkarVurdert(vilkår: Vilkår) = when (vilkår.vurdering) {
        Vurdering.Avslag -> JaNei.JA
        Vurdering.Innvilget -> JaNei.NEI
        Vurdering.Uavklart -> null
    }

    private fun vilkarVurdertHvisEksisterer(vilkår: Either<VilkårEksistererIkke, Vilkår>) = vilkår.fold(
        { null },
        {
            vilkarVurdert(it)
        },
    )

    private fun mapBeregning(
        vedtak: VedtakEndringIYtelse,
        beregning: Beregning,
    ): List<StønadstatistikkDto.Månedsbeløp> {
        val alleFradrag = beregning.tilFradragPerMåned()
        val månedsberegninger = beregning.getMånedsberegninger().associateBy { it.måned }

        val uføregrunnlag = when (vedtak.sakinfo().type) {
            Sakstype.ALDER -> null
            Sakstype.UFØRE -> {
                vedtak.behandling.vilkårsvurderinger.hentUføregrunnlag()
            }
        }

        val månederIVedtakOgBeregning =
            vedtak.periode.måneder().toSet().intersect(beregning.periode.måneder().toSet()).toList()

        return månederIVedtakOgBeregning.map { måned ->
            val fradrag = alleFradrag[måned]?.let { maxAvForventetInntektOgArbeidsInntekt(it) } ?: emptyList()

            val uføregrad = uføregrunnlag?.let {
                it.singleOrNull { it.periode.måneder().contains(måned) }
                    ?: throw IllegalStateException("Uføregrunnlag mangler eller har overlappende perioder")
            }?.uføregrad

            val månedsberegning =
                månedsberegninger[måned] ?: throw IllegalStateException("Beregning mangler måned $måned")
            StønadstatistikkDto.Månedsbeløp(
                fradrag = fradrag.map {
                    StønadstatistikkDto.Fradrag(
                        fradragstype = it.fradragstype.toString(),
                        beløp = it.månedsbeløp.toLong(),
                        tilhører = it.tilhører.toString(),
                        erUtenlandsk = it.utenlandskInntekt != null,
                    )
                },
                sats = månedsberegning.getSatsbeløp().roundToInt().toLong(),
                fradragSum = månedsberegning.getSumFradrag().toLong(),
                måned = månedsberegning.periode.fraOgMed.toString(),
                utbetales = månedsberegning.getSumYtelse().toLong(),
                stonadsklassifisering = stønadsklassifisering(vedtak.behandling, månedsberegning),
                uføregrad = uføregrad?.value,
            )
        }
    }

    private fun Beregning?.tilFradragPerMåned(): Map<Måned, List<FradragForMåned>> = this?.let { beregning ->
        beregning.getFradrag()
            .flatMap { FradragFactory.periodiser(it) }
    }?.groupBy { it.måned } ?: emptyMap()

    private fun maxAvForventetInntektOgArbeidsInntekt(fradragForMåned: List<FradragForMåned>): List<FradragForMåned> {
        val (IEUogArbeidsInntekt, rest) = fradragForMåned.partition {
            listOf(
                Fradragstype.ForventetInntekt,
                Fradragstype.Arbeidsinntekt,
            ).contains(it.fradragstype)
        }
        return listOfNotNull(IEUogArbeidsInntekt.maxByOrNull { it.månedsbeløp }) + rest
    }

    private fun stønadsklassifisering(
        behandling: Stønadsbehandling,
        månedsberegning: Månedsberegning,
    ): StønadsklassifiseringDto {
        val bosituasjon = behandling.grunnlagsdata.bosituasjon.singleOrNull {
            it.periode inneholder månedsberegning.periode
        } ?: throw IllegalStateException("Bosituasjon mangler eller har overlappende perioder")

        return when (bosituasjon) {
            is Bosituasjon.Fullstendig -> bosituasjon.stønadsklassifisering()
            is Bosituasjon.Ufullstendig -> StønadsklassifiseringDto.UFULLSTENDIG_GRUNNLAG.also {
                log.error("Kunne ikke avgjøre stønadsklassifisering ved utsending av statistikk, siden grunnlaget var ufullstendig. Behandling id: ${behandling.id}")
            }
        }
    }
}
