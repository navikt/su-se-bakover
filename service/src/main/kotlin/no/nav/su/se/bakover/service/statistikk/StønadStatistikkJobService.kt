package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import arrow.core.toNonEmptyListOrNull
import behandling.domain.Stønadsbehandling
import beregning.domain.Beregning
import beregning.domain.Månedsberegning
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.Job
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobStatistics
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.WriteChannelConfiguration
import no.nav.su.se.bakover.common.domain.JaNei
import no.nav.su.se.bakover.common.domain.extensions.hasOneElement
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkRepo
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vilkår.hentUføregrunnlag
import org.slf4j.LoggerFactory
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadsklassifiseringDto.Companion.stønadsklassifisering
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkDto.Fradrag
import statistikk.domain.StønadstatistikkMåned
import vedtak.domain.Vedtak
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.vurderinger.domain.VilkårEksistererIkke
import java.nio.channels.Channels
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
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
            val månedstatistikk = stønadStatistikkRepo.hentMånedStatistikk(måned)
            StønadBigQueryService.lastTilBigQuery(måned, månedstatistikk)
        }
    }

    fun lagMånedligStønadstatistikk(
        clock: Clock,
        måned: YearMonth,
    ) {
        val alleVedtak = vedtakRepo.hentVedtakForMåned(Måned.fra(måned))
        val vedtakMedMånedsbeløp = alleVedtak.filter {
            it !is VedtakAvslagVilkår && it !is VedtakAvslagBeregning
        }
        vedtakMedMånedsbeløp.groupBy { it.behandling.sakId }.filter {
            // Opphørsvedtak har en til og med lik opprinnelig vedtak, men stønadstatistikk er kun interessert i opphøret da det inntraff.
            val siste = it.value.maxBy { it.opprettet }
            val opphørtTidligereMåned = !(siste is Opphørsvedtak && siste.periode.fraOgMed < måned.atDay(1))
            opphørtTidligereMåned
        }.forEach {
            val siste = it.value.maxBy { it.opprettet }
            val behandling = siste.behandling as Stønadsbehandling
            val sak = behandling.sakinfo()

            val månedsbeløp = månedsbeløperBasertPåVedtak(clock, måned, siste, alleVedtak).singleOrNull {
                YearMonth.from(LocalDate.parse(it.måned, DateTimeFormatter.ISO_DATE)) == måned
            }

            val stønadstatistikk = toStønadstatistikk(
                clock,
                måned,
                sak,
                siste,
                behandling,
                månedsbeløp,
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

    private fun månedsbeløperBasertPåVedtak(clock: Clock, måned: YearMonth, siste: Vedtak, alleVedtak: List<Vedtak>) =
        when (siste) {
            is VedtakInnvilgetRevurdering -> månedsbeløper(siste, siste.beregning)
            is VedtakInnvilgetSøknadsbehandling -> månedsbeløper(siste, siste.beregning)
            is VedtakInnvilgetRegulering -> månedsbeløper(siste, siste.beregning)
            is VedtakGjenopptakAvYtelse -> {
                val beregningSomGjenopptas = GjeldendeVedtaksdata(
                    periode = Måned.fra(måned),
                    vedtakListe = alleVedtak.filterIsInstance<VedtakSomKanRevurderes>()
                        .filter { it.beregning != null }.toNonEmptyListOrNull()
                        ?: throw IllegalStateException("Mangler vedtak med beregning i periode som skal ha blitt gjenopptatt"),
                    clock = clock,
                ).gjeldendeVedtakForMåned(Måned.fra(måned))?.beregning
                    ?: throw IllegalStateException("Mangler vedtak med beregning i periode som skal ha blitt gjenopptatt")

                månedsbeløper(siste, beregningSomGjenopptas)
            }

            is VedtakOpphørMedUtbetaling,
            is Opphørsvedtak,
            is VedtakStansAvYtelse,
            -> emptyList()

            else -> throw IllegalStateException("Ikke tatt høyde for ${siste::class.simpleName} ved generering av statistikk")
        }

    private fun månedsbeløper(
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
                    Fradrag(
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
                fribeløpEps = månedsberegning.getFribeløpForEps().toLong(),
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

    private fun toStønadstatistikk(
        clock: Clock,
        måned: YearMonth,
        sak: SakInfo,
        siste: Vedtak,
        behandling: Stønadsbehandling,
        månedsbeløp: StønadstatistikkDto.Månedsbeløp?,
    ): StønadstatistikkMåned {
        return StønadstatistikkMåned(
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
            årsakStans = when (siste) {
                is VedtakStansAvYtelse -> siste.behandling.revurderingsårsak.årsak.name
                else -> null
            },
            behandlendeEnhetKode = "4815",

            stonadsklassifisering = månedsbeløp?.stonadsklassifisering,
            sats = månedsbeløp?.sats,
            utbetales = månedsbeløp?.utbetales,
            fradragSum = månedsbeløp?.fradragSum,
            uføregrad = månedsbeløp?.uføregrad,
            fribeløpEps = månedsbeløp?.fribeløpEps,

            alderspensjon = fradragOmFinnes(
                Fradragstype.Kategori.Alderspensjon,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            alderspensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.Alderspensjon,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            arbeidsavklaringspenger = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsavklaringspenger,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            arbeidsavklaringspengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsavklaringspenger,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            arbeidsinntekt = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsinntekt,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            arbeidsinntektEps = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsinntekt,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            omstillingsstønad = fradragOmFinnes(
                Fradragstype.Kategori.Omstillingsstønad,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            omstillingsstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Omstillingsstønad,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            avtalefestetPensjon = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjon,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            avtalefestetPensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjon,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            avtalefestetPensjonPrivat = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjonPrivat,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            avtalefestetPensjonPrivatEps = fradragOmFinnes(
                Fradragstype.Kategori.AvtalefestetPensjonPrivat,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            bidragEtterEkteskapsloven = fradragOmFinnes(
                Fradragstype.Kategori.BidragEtterEkteskapsloven,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            bidragEtterEkteskapslovenEps = fradragOmFinnes(
                Fradragstype.Kategori.BidragEtterEkteskapsloven,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            dagpenger = fradragOmFinnes(
                Fradragstype.Kategori.Dagpenger,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            dagpengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Dagpenger,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            fosterhjemsgodtgjørelse = fradragOmFinnes(
                Fradragstype.Kategori.Fosterhjemsgodtgjørelse,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            fosterhjemsgodtgjørelseEps = fradragOmFinnes(
                Fradragstype.Kategori.Fosterhjemsgodtgjørelse,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            gjenlevendepensjon = fradragOmFinnes(
                Fradragstype.Kategori.Gjenlevendepensjon,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            gjenlevendepensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.Gjenlevendepensjon,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            introduksjonsstønad = fradragOmFinnes(
                Fradragstype.Kategori.Introduksjonsstønad,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            introduksjonsstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Introduksjonsstønad,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            kapitalinntekt = fradragOmFinnes(
                Fradragstype.Kategori.Kapitalinntekt,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            kapitalinntektEps = fradragOmFinnes(
                Fradragstype.Kategori.Kapitalinntekt,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            kontantstøtte = fradragOmFinnes(
                Fradragstype.Kategori.Kontantstøtte,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            kontantstøtteEps = fradragOmFinnes(
                Fradragstype.Kategori.Kontantstøtte,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            kvalifiseringsstønad = fradragOmFinnes(
                Fradragstype.Kategori.Kvalifiseringsstønad,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            kvalifiseringsstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Kvalifiseringsstønad,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            navYtelserTilLivsopphold = fradragOmFinnes(
                Fradragstype.Kategori.NAVytelserTilLivsopphold,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            navYtelserTilLivsoppholdEps = fradragOmFinnes(
                Fradragstype.Kategori.NAVytelserTilLivsopphold,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            offentligPensjon = fradragOmFinnes(
                Fradragstype.Kategori.OffentligPensjon,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            offentligPensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.OffentligPensjon,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            privatPensjon = fradragOmFinnes(
                Fradragstype.Kategori.PrivatPensjon,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            privatPensjonEps = fradragOmFinnes(
                Fradragstype.Kategori.PrivatPensjon,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            sosialstønad = fradragOmFinnes(
                Fradragstype.Kategori.Sosialstønad,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            sosialstønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Sosialstønad,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            statensLånekasse = fradragOmFinnes(
                Fradragstype.Kategori.StatensLånekasse,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            statensLånekasseEps = fradragOmFinnes(
                Fradragstype.Kategori.StatensLånekasse,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            supplerendeStønad = fradragOmFinnes(
                Fradragstype.Kategori.SupplerendeStønad,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            supplerendeStønadEps = fradragOmFinnes(
                Fradragstype.Kategori.SupplerendeStønad,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            sykepenger = fradragOmFinnes(
                Fradragstype.Kategori.Sykepenger,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            sykepengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Sykepenger,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            tiltakspenger = fradragOmFinnes(
                Fradragstype.Kategori.Tiltakspenger,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            tiltakspengerEps = fradragOmFinnes(
                Fradragstype.Kategori.Tiltakspenger,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            ventestønad = fradragOmFinnes(
                Fradragstype.Kategori.Ventestønad,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            ventestønadEps = fradragOmFinnes(
                Fradragstype.Kategori.Ventestønad,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            uføretrygd = fradragOmFinnes(
                Fradragstype.Kategori.Uføretrygd,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            uføretrygdEps = fradragOmFinnes(
                Fradragstype.Kategori.Arbeidsinntekt,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            forventetInntekt = fradragOmFinnes(
                Fradragstype.Kategori.ForventetInntekt,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            forventetInntektEps = fradragOmFinnes(
                Fradragstype.Kategori.ForventetInntekt,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            avkortingUtenlandsopphold = fradragOmFinnes(
                Fradragstype.Kategori.AvkortingUtenlandsopphold,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            avkortingUtenlandsoppholdEps = fradragOmFinnes(
                Fradragstype.Kategori.AvkortingUtenlandsopphold,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            underMinstenivå = fradragOmFinnes(
                Fradragstype.Kategori.UnderMinstenivå,
                FradragTilhører.BRUKER,
                månedsbeløp,
            ),
            underMinstenivåEps = fradragOmFinnes(
                Fradragstype.Kategori.UnderMinstenivå,
                FradragTilhører.EPS,
                månedsbeløp,
            ),

            annet = fradragOmFinnes(Fradragstype.Kategori.Annet, FradragTilhører.BRUKER, månedsbeløp),
            annetEps = fradragOmFinnes(Fradragstype.Kategori.Annet, FradragTilhører.EPS, månedsbeløp),

        )
    }

    private fun fradragOmFinnes(
        fradragstype: Fradragstype.Kategori,
        tilhører: FradragTilhører,
        månedsbeløp: StønadstatistikkDto.Månedsbeløp?,
    ): Int? {
        return månedsbeløp?.fradrag?.singleOrNull {
            it.fradragstype == fradragstype.name && it.tilhører == tilhører.name
        }?.beløp?.toInt()
    }
}

object StønadBigQueryService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private const val LOCATION = "europe-north1"

    fun lastTilBigQuery(måned: YearMonth, data: List<StønadstatistikkMåned>) {
        log.info("Sender ${data.size} rader for stønadstatistikk fra databasen, overfører for måned $måned")
        writeToBigQuery(data)
        log.info("Slutter jobb Stønadstatistikk")
    }

    private fun writeToBigQuery(data: List<StønadstatistikkMåned>) {
        /*
            https://docs.nais.io/persistence/bigquery/how-to/connect/?h=bigquery
            defaulty inject basert på yaml filens referanses
         */
        val project: String = System.getenv("GCP_TEAM_PROJECT_ID")

        val bq = createBigQueryClient(project)

        val stoenadtable = "stoenadstatistikk"
        val stoenadCSV = data.toCSV()
        log.info("Skriver ${stoenadCSV.length} bytes til BigQuery-tabell: $stoenadtable")

        val jobStoenad = writeCsvToBigQueryTable(
            bigQueryClient = bq,
            project = project,
            tableName = stoenadtable,
            csvData = stoenadCSV,
        )

        log.info("Saksstatistikkjobb: ${jobStoenad.getStatistics<JobStatistics.LoadStatistics>()}")
    }

    private fun createBigQueryClient(project: String): BigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setLocation(LOCATION)
            .setProjectId(project)
            .build()
            .service

    val dataset = "statistikk"
    private fun writeCsvToBigQueryTable(
        bigQueryClient: BigQuery,
        project: String,
        tableName: String,
        csvData: String,
    ): Job {
        val jobId = JobId.newBuilder()
            .setLocation(LOCATION)
            .setJob(UUID.randomUUID().toString())
            .build()

        val tableId = TableId.of(project, dataset, tableName)

        log.info("Writing csv to bigquery. id: $jobId, project: $project, table: $tableId")

        val writeConfig = WriteChannelConfiguration.newBuilder(tableId)
            .setFormatOptions(FormatOptions.csv())
            .build()

        val writer = try {
            bigQueryClient.writer(jobId, writeConfig)
        } catch (e: Exception) {
            throw RuntimeException("BigQuery writer creation failed: ${e.message}", e)
        }

        try {
            writer.use { channel ->
                Channels.newOutputStream(channel).use { os ->
                    os.write(csvData.toByteArray())
                }
            }
        } catch (e: Exception) {
            log.error("Failed to write CSV data to BigQuery stream: ${e.message}", e)
            throw RuntimeException("Error during CSV write to BigQuery", e)
        }

        val job = writer.job
        job.waitFor()

        return job
    }
}

/*
Endrer du rekkefølgen her må det også gjenspeiles i bigquery
Rekkefølge i BQ:
       "id", "maned", "vedtaksdato", "personnummer", "vedtakFraOgMed", "vedtakTilOgMed", "sakId",
       "funksjonellTid", "tekniskTid", "stonadstype", "personnummerEps", "vedtakstype", "vedtaksresultat",
       "opphorsgrunn", "opphorsdato", "arsakStans", "behandlendeEnhetKode", "stonadsklassifisering", "sats",
       "utbetales", "fradragsum", "uforegrad", "alderspensjon", "alderspensjoneps", "arbeidsavklaringspenger",
       "arbeidsavklaringspengereps", "arbeidsinntekt", "arbeidsinntekteps", "omstillingsstonad", "omstillingsstonadeps",
       "avtalefestetpensjon", "avtalefestetpensjoneps", "avtalefestetpensjonprivat", "avtalefestetpensjonprivateps",
       "bidragetterekteskapsloven", "bidragetterekteskapsloveneps", "dagpenger", "dagpengereps",
       "fosterhjemsgodtgjorelse", "fosterhjemsgodtgjorelseeps", "gjenlevendepensjon", "gjenlevendepensjoneps",
       "introduksjonsstonad", "introduksjonsstonadeps", "kapitalinntekt", "kapitalinntekteps", "kontantstotte",
       "kontantstotteeps", "kvalifiseringsstonad", "kvalifiseringsstonadeps", "navytelsertillivsopphold",
       "navytelsertillivsoppholdeps", "offentligpensjon", "offentligpensjoneps", "privatpensjon",
       "privatpensjoneps", "sosialstonad", "sosialstonadeps", "statenslanekasse", "statenslanekasseeps",
       "supplerendestonad", "supplerendestonadeps", "sykepenger", "sykepengereps", "tiltakspenger",
       "tiltakspengereps", "ventestonad", "ventestonadeps", "uforetrygd", "uforetrygdeps", "forventetinntekt",
       "forventetinntekteps", "avkortingutenlandsopphold", "avkortingutenlandsoppholdeps",
       "underminsteniva", "underminstenivaeps", "annet", "anneteps", "lastetdato"
 */
fun List<StønadstatistikkMåned>.toCSV(): String {
    return buildString {
        for (dto in this@toCSV) {
            appendLine(
                listOf(
                    dto.id.toString(),
                    dto.måned.toString(),
                    dto.vedtaksdato.toString(),
                    dto.personnummer.toString(),
                    dto.vedtakFraOgMed.toString(),
                    dto.vedtakTilOgMed.toString(),
                    dto.sakId.toString(),
                    dto.funksjonellTid.toString(),
                    dto.tekniskTid.toString(),
                    dto.stonadstype.toString(),
                    dto.personNummerEps.toString(),
                    dto.vedtakstype.toString(),
                    dto.vedtaksresultat.toString(),
                    dto.opphorsgrunn.orEmpty(),
                    dto.opphorsdato?.toString().orEmpty(),
                    dto.årsakStans.orEmpty(),
                    dto.behandlendeEnhetKode,
                    dto.stonadsklassifisering.toString(),
                    dto.sats?.toString().orEmpty(),
                    dto.utbetales?.toString().orEmpty(),
                    dto.fradragSum?.toString().orEmpty(),
                    dto.uføregrad?.toString().orEmpty(),
                    dto.alderspensjon?.toString().orEmpty(),
                    dto.alderspensjonEps?.toString().orEmpty(),
                    dto.arbeidsavklaringspenger?.toString().orEmpty(),
                    dto.arbeidsavklaringspengerEps?.toString().orEmpty(),
                    dto.arbeidsinntekt?.toString().orEmpty(),
                    dto.arbeidsinntektEps?.toString().orEmpty(),
                    dto.omstillingsstønad?.toString().orEmpty(),
                    dto.omstillingsstønadEps?.toString().orEmpty(),
                    dto.avtalefestetPensjon?.toString().orEmpty(),
                    dto.avtalefestetPensjonEps?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivat?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivatEps?.toString().orEmpty(),
                    dto.bidragEtterEkteskapsloven?.toString().orEmpty(),
                    dto.bidragEtterEkteskapslovenEps?.toString().orEmpty(),
                    dto.dagpenger?.toString().orEmpty(),
                    dto.dagpengerEps?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjørelse?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjørelseEps?.toString().orEmpty(),
                    dto.gjenlevendepensjon?.toString().orEmpty(),
                    dto.gjenlevendepensjonEps?.toString().orEmpty(),
                    dto.introduksjonsstønad?.toString().orEmpty(),
                    dto.introduksjonsstønadEps?.toString().orEmpty(),
                    dto.kapitalinntekt?.toString().orEmpty(),
                    dto.kapitalinntektEps?.toString().orEmpty(),
                    dto.kontantstøtte?.toString().orEmpty(),
                    dto.kontantstøtteEps?.toString().orEmpty(),
                    dto.kvalifiseringsstønad?.toString().orEmpty(),
                    dto.kvalifiseringsstønadEps?.toString().orEmpty(),
                    dto.navYtelserTilLivsopphold?.toString().orEmpty(),
                    dto.navYtelserTilLivsoppholdEps?.toString().orEmpty(),
                    dto.offentligPensjon?.toString().orEmpty(),
                    dto.offentligPensjonEps?.toString().orEmpty(),
                    dto.privatPensjon?.toString().orEmpty(),
                    dto.privatPensjonEps?.toString().orEmpty(),
                    dto.sosialstønad?.toString().orEmpty(),
                    dto.sosialstønadEps?.toString().orEmpty(),
                    dto.statensLånekasse?.toString().orEmpty(),
                    dto.statensLånekasseEps?.toString().orEmpty(),
                    dto.supplerendeStønad?.toString().orEmpty(),
                    dto.supplerendeStønadEps?.toString().orEmpty(),
                    dto.sykepenger?.toString().orEmpty(),
                    dto.sykepengerEps?.toString().orEmpty(),
                    dto.tiltakspenger?.toString().orEmpty(),
                    dto.tiltakspengerEps?.toString().orEmpty(),
                    dto.ventestønad?.toString().orEmpty(),
                    dto.ventestønadEps?.toString().orEmpty(),
                    dto.uføretrygd?.toString().orEmpty(),
                    dto.uføretrygdEps?.toString().orEmpty(),
                    dto.forventetInntekt?.toString().orEmpty(),
                    dto.forventetInntektEps?.toString().orEmpty(),
                    dto.avkortingUtenlandsopphold?.toString().orEmpty(),
                    dto.avkortingUtenlandsoppholdEps?.toString().orEmpty(),
                    dto.underMinstenivå?.toString().orEmpty(),
                    dto.underMinstenivåEps?.toString().orEmpty(),
                    dto.annet?.toString().orEmpty(),
                    dto.annetEps?.toString().orEmpty(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm")),
                ).joinToString(",") { escapeCsv(it) },
            )
        }
    }
}

private fun escapeCsv(field: String): String {
    val needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n")
    val escaped = field.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}
