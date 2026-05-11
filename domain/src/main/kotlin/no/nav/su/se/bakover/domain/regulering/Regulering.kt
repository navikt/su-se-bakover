package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.domain.Stønadsbehandling
import behandling.regulering.domain.beregning.KunneIkkeBeregneRegulering
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import beregning.domain.Beregning
import beregning.domain.BeregningStrategyFactory
import beregning.domain.Månedsberegning
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.sak.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import satser.domain.Satskategori
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.common.domain.Vurdering
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.UføreVilkår
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
import økonomi.domain.simulering.Simulering
import java.math.BigDecimal
import java.time.Clock
import kotlin.collections.ifEmpty
import kotlin.to

private val log: Logger = LoggerFactory.getLogger("Regulering")

sealed interface Regulering : Stønadsbehandling {
    override val id: ReguleringId
    override val beregning: Beregning?
    override val simulering: Simulering?
    override val eksterneGrunnlag: EksterneGrunnlag get() = StøtterIkkeHentingAvEksternGrunnlag
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering
    override val vilkårsvurderinger: VilkårsvurderingerRevurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringstype: Reguleringstype

    val eksterntRegulerteBeløp: EksterntRegulerteBeløp?

    fun erÅpen(): Boolean

    /** true dersom dette er en iverksatt regulering, false ellers. */
    val erFerdigstilt: Boolean
}

data class SakTilRegulering(
    val sakInfo: SakInfo,
    val gjeldendeVedtaksdata: GjeldendeVedtaksdata,
)

fun SakTilRegulering.opprettReguleringForAutomatiskEllerManuellBehandling(
    clock: Clock,
    alleEksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
): Either<ÅrsakRevurdering, OpprettetRegulering> {
    val eksterntRegulerteBeløp = alleEksterntRegulerteBeløp.singleOrNull { it.brukerFnr == sakInfo.fnr }
        ?: throw IllegalStateException("Sak har feil i fradrag fra ekstern kilde. Sak=${sakInfo.saksnummer}")

    val (reguleringstype, grunnlagsdataOgVilkårsvurderinger) = utledReguleringstypeOgOppdaterFradrag(
        gjeldendeVedtaksdata,
        eksterntRegulerteBeløp,
        clock,
    ).getOrElse { return it.left() }

    return OpprettetRegulering.opprett(
        sakInfo = sakInfo,
        reguleringstype = reguleringstype,
        grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
        eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        clock = clock,
    ).right()
}

fun utledReguleringstypeOgOppdaterFradrag(
    gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    clock: Clock,
): Either<ÅrsakRevurdering, Pair<Reguleringstype, GrunnlagsdataOgVilkårsvurderingerRevurdering>> {
    val reguleringstypeVedtaksdata = gjeldendeVedtaksdata.utledReguleringstype()

    val (reguleringstypeFradrag, fradragOppdatertMedEksterneBeløp) = utledReguleringstypeOgOppdaterFradrag(
        fradrag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
        eksterntRegulerteBeløp = eksterntRegulerteBeløp,
    ).getOrElse {
        return it.left()
    }

    val (reguleringstypeIeu, vilkårMedOppdatertIeu) = regulerForventetIeuOmGyldig(
        vilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger,
        eksterntRegulerteBeløp = eksterntRegulerteBeløp,
        clock = clock,
    ).getOrElse { return it.left() }

    val reguleringstype = Reguleringstype.utledReguleringsTypeFrom(
        reguleringstype1 = reguleringstypeVedtaksdata,
        reguleringstype2 = Reguleringstype.utledReguleringsTypeFrom(
            reguleringstypeFradrag,
            reguleringstypeIeu,
        ),
    )

    val oppdaterteGrunnlagOgVilkår = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger
        .oppdaterFradragsgrunnlag(fradragOppdatertMedEksterneBeløp)
        .oppdaterVilkårsvurderinger(vilkårMedOppdatertIeu)
    return (reguleringstype to oppdaterteGrunnlagOgVilkår).right()
}

fun regulerForventetIeuOmGyldig(
    vilkårsvurderinger: VilkårsvurderingerRevurdering,
    eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    clock: Clock,
): Either<ÅrsakRevurdering, Pair<Reguleringstype, VilkårsvurderingerRevurdering>> {
    if (vilkårsvurderinger is VilkårsvurderingerRevurdering.Alder) {
        return (Reguleringstype.AUTOMATISK to vilkårsvurderinger).right()
    } else {
        val eksisterendeVilkårMedIeu = when ((vilkårsvurderinger as VilkårsvurderingerRevurdering.Uføre).uføre) {
            UføreVilkår.IkkeVurdert -> throw IllegalStateException("Kan ikke regulere en ikke vurdert uføretrygd")
            is UføreVilkår.Vurdert -> (vilkårsvurderinger.uføre as UføreVilkår.Vurdert)
        }
        val uføreGrunnlagMedIeu = eksisterendeVilkårMedIeu.vurderingsperioder.mapNotNull { it.grunnlag }.filter {
            it.uføregrad.value < 100
        }
        if (uføreGrunnlagMedIeu.isEmpty()) {
            return (Reguleringstype.AUTOMATISK to vilkårsvurderinger).right()
        }

        val eksterntRegulertIeu = eksterntRegulerteBeløp.inntektEtterUføre?.etterRegulering?.toInt()
            ?: return (Reguleringstype.MANUELL(ÅrsakTilManuellRegulering.ManglerIeuFraPesys()) to vilkårsvurderinger).right()

        // Er en loop fordi typen er en list, men under en regulering vil det alltid bare være en periode
        for (vilkårPeriodeGrunnlag in uføreGrunnlagMedIeu) {
            val bruktBeløp = BigDecimal(vilkårPeriodeGrunnlag.forventetInntekt).setScale(2)
            if (bruktBeløp != eksterntRegulerteBeløp.inntektEtterUføre.førRegulering) {
                return ÅrsakRevurdering(
                    årsak = ÅrsakRevurdering.Årsak.DIFFERANSE_MED_EKSTERNE_BELØP,
                    diffBeløp = listOf(
                        ÅrsakRevurdering.BeløperMedDiff.Fradrag(
                            fradragstype = Fradragstype.ForventetInntekt,
                            tilhører = FradragTilhører.BRUKER,
                            eksisterendeBeløp = bruktBeløp,
                            nyttBeløp = eksterntRegulerteBeløp.inntektEtterUføre.førRegulering,
                        ),
                    ),
                ).left()
            }
        }

        val vilkårMedOppdatertRegulertIeu = vilkårsvurderinger.copy(
            uføre = eksisterendeVilkårMedIeu.regulerForventetIEU(clock, eksterntRegulertIeu),
        )
        return (Reguleringstype.AUTOMATISK to vilkårMedOppdatertRegulertIeu).right()
    }
}

fun hentGjeldendeVedtaksdataForRegulering(
    fraOgMedMåned: Måned,
    sakInfo: SakInfo,
    vedtakSomKanRevurderes: List<VedtakSomKanRevurderes>,
    clock: Clock,
): Either<BleIkkeRegulert, GjeldendeVedtaksdata> {
    val (_, saksnummer, _, saktype) = sakInfo
    val vedtakstidslinje =
        vedtakSomKanRevurderes.lagTidslinje()?.fjernMånederFør(fraOgMedMåned)
    val periode = vedtakstidslinje.let { tidslinje ->
        (tidslinje ?: emptyList())
            .filterNot { it.erOpphør() }
            .map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
            .minsteAntallSammenhengendePerioder()
            .ifEmpty {
                log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $fraOgMedMåned")
                return BleIkkeRegulert.IkkeLøpendeSak(saksnummer).left()
            }
    }.also {
        if (it.count() != 1) {
            return BleIkkeRegulert.MåRegulereMedRevurdering(
                saksnummer = saksnummer,
                årsak = ÅrsakRevurdering(ÅrsakRevurdering.Årsak.IKKE_KONTINUERLIG_VEDTAKSLINJE),
            ).left()
        }
    }.single()

    val gjeldendeVedtaksdata = vedtakSomKanRevurderes
        .ifEmpty {
            log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere for perioden (${periode.fraOgMed}, ${periode.tilOgMed})")
            return BleIkkeRegulert.IkkeLøpendeSak(saksnummer).left()
        }.let { vedtakSomKanRevurderes ->
            GjeldendeVedtaksdata(
                periode = periode,
                vedtakListe = vedtakSomKanRevurderes.toNonEmptyList(),
                clock = clock,
            )
        }

    gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent(saktype)
        .onLeft { konsistensproblemer ->
            log.error("Kunne ikke opprette regulering for saksnummer $saksnummer. Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]")
            return BleIkkeRegulert.MåRegulereMedRevurdering(
                saksnummer = saksnummer,
                årsak = ÅrsakRevurdering(ÅrsakRevurdering.Årsak.INKONSISTENTE_GRUNNLAG_OG_VILKÅR),
            ).left()
        }

    return gjeldendeVedtaksdata.right()
}

fun beregnerUtenforToleransegrenser(
    sak: Sak,
    regulering: OpprettetRegulering,
    satsFactory: SatsFactory,
    clock: Clock,
): ÅrsakRevurdering? {
    if (regulering.vilkårsvurderinger.resultat() is Vurdering.Avslag) {
        return ÅrsakRevurdering(
            årsak = ÅrsakRevurdering.Årsak.REGULERING_FØRER_TIL_AVSLAG,
        )
    }

    val beregning = beregnRegulering(
        satsFactory = satsFactory,
        begrunnelse = null,
        regulering,
        clock = clock,
    ).getOrElse {
        throw RuntimeException("Regulering for saksnummer ${regulering.saksnummer}: Vi klarte ikke å beregne. Underliggende grunn ${it.feil}")
    }

    val utenforToleransegrenser = beregning.getMånedsberegninger().mapNotNull { månedsberegning ->
        val gjeldendeUtbetaling = sak.hentGjeldendeUtbetaling(månedsberegning.periode.fraOgMed)
            .getOrElse { throw IllegalStateException("Finner ikke gjeldende utbetaling for sak som skal reguleres") }
            .beløp

        val feilutbetaling = månedsberegning.getSumYtelse() < gjeldendeUtbetaling
        val toleransegrense = gjeldendeUtbetaling * 1.1
        val over10prosentEndring = månedsberegning.getSumYtelse() > toleransegrense
        if (feilutbetaling) {
            ÅrsakRevurdering(
                årsak = ÅrsakRevurdering.Årsak.REGULERING_BLIR_FEILUTBETALING,
            )
        } else if (over10prosentEndring) {
            ÅrsakRevurdering(
                årsak = ÅrsakRevurdering.Årsak.REGULERING_ER_OVER_TOLERANSEGRENSE,
                diffBeløp = listOf(
                    ÅrsakRevurdering.BeløperMedDiff.BeregningOverToleranse(
                        eksisterendeBeløp = BigDecimal(gjeldendeUtbetaling),
                        nyttBeløp = BigDecimal(månedsberegning.getSumYtelse()),
                        toleransegrense = BigDecimal.valueOf(toleransegrense),
                    ),
                ),
            )
        } else {
            null
        }
    }
    return if (utenforToleransegrenser.isNotEmpty()) {
        utenforToleransegrenser.first()
    } else {
        null
    }
}

fun beregnRegulering(
    satsFactory: SatsFactory,
    begrunnelse: String?,
    regulering: ReguleringUnderBehandling,
    clock: Clock,
): Either<KunneIkkeBeregneRegulering.BeregningFeilet, Beregning> {
    return Either.catch {
        BeregningStrategyFactory(
            clock = clock,
            satsFactory = satsFactory,
        ).beregn(
            grunnlagsdataOgVilkårsvurderinger = regulering.grunnlagsdataOgVilkårsvurderinger,
            begrunnelse = begrunnelse,
            sakstype = regulering.sakstype,
        )
    }.mapLeft {
        KunneIkkeBeregneRegulering.BeregningFeilet(feil = it)
    }
}

fun GjeldendeVedtaksdata.erRegulertMedNyttGrunnbeløp(
    etterspurtMai: Måned,
    sakstype: Sakstype,
    satsFactory: SatsFactory,
): Boolean {
    val sisteBeløper = SisteGrunnbeløpOgSatser(
        grunnbeløp = satsFactory.grunnbeløp(etterspurtMai).grunnbeløpPerÅr,
        garantipensjonOrdinær = satsFactory.ordinærAlder(etterspurtMai).garantipensjonForMåned.garantipensjonPerÅr,
        garantipensjonHøy = satsFactory.høyAlder(etterspurtMai).garantipensjonForMåned.garantipensjonPerÅr,
    )

    val beregning = hentMånedsberegning(etterspurtMai).singleOrNull()
        ?: throw (IllegalStateException("Forventer kun én månedsberegning per måned"))

    return beregning.erRegulertMedNyttGrunnbeløp(sakstype, sisteBeløper)
}

fun Månedsberegning.erRegulertMedNyttGrunnbeløp(
    sakstype: Sakstype,
    sisteBeløper: SisteGrunnbeløpOgSatser,
): Boolean {
    val benyttetG = getBenyttetGrunnbeløp()
    val kategori = getSats()
    val benyttetSats = fullSupplerendeStønadForMåned.sats.sats.toDouble()

    return when (sakstype) {
        Sakstype.UFØRE -> benyttetG == sisteBeløper.grunnbeløp
        Sakstype.ALDER -> when (kategori) {
            Satskategori.ORDINÆR -> benyttetSats == sisteBeløper.garantipensjonOrdinær.toDouble()
            Satskategori.HØY -> benyttetSats == sisteBeløper.garantipensjonHøy.toDouble()
        }
    }
}
