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
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.sak.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.common.domain.Vurdering
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
import økonomi.domain.simulering.Simulering
import java.time.Clock
import kotlin.collections.ifEmpty

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

    /**
     * Supplementet inneholder informasjon som skal brukes for å oppdatere grunnlagene
     * Supplementet hentes fra eksterne kilder
     */
    // TODO AUTO-REG-26 bytt med EksterntRegulerteBeløp
    val eksternSupplementRegulering: EksternSupplementRegulering?

    fun erÅpen(): Boolean

    /** true dersom dette er en iverksatt regulering, false ellers. */
    val erFerdigstilt: Boolean
}

fun Sak.opprettReguleringForAutomatiskEllerManuellBehandling(
    clock: Clock,
    gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    alleEksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
    satsFactory: SatsFactory,
): Either<Sak.KanIkkeRegulere, OpprettetRegulering> {
    if (reguleringer.filterIsInstance<ReguleringUnderBehandling>().isNotEmpty()) {
        throw IllegalStateException("Skal ikke kunne finnes åpne reguleringer på dette stadiet. Skal valideres i tidligere steg")
    }
    val eksterntRegulerteBeløp = alleEksterntRegulerteBeløp.singleOrNull { it.brukerFnr == fnr }
        ?: throw IllegalStateException("Sak har feil i fradrag fra ekstern kilde. Sak=$saksnummer")

    val reguleringstypeVedGenerelleProblemer = gjeldendeVedtaksdata.utledReguleringstype()

    val (reguleringstypeBasertPåFradrag, fradragOppdatertMedEksterneBeløp) = utledReguleringstypeOgOppdaterFradrag(
        fradrag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
        eksterntRegulerteBeløp = eksterntRegulerteBeløp,
    ).getOrElse {
        return it.left()
    }

    // utledning av reguleringstype bør gjøre mer helhetlig, og muligens kun 1 gang. Dette er en midlertidig løsning.
    val reguleringstype = Reguleringstype.utledReguleringsTypeFrom(
        reguleringstype1 = reguleringstypeVedGenerelleProblemer,
        reguleringstype2 = reguleringstypeBasertPåFradrag,
    )

    val opprettetRegulering = OpprettetRegulering(
        id = ReguleringId.generer(),
        opprettet = Tidspunkt.now(clock),
        sakId = id,
        saksnummer = saksnummer,
        saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
        fnr = fnr,
        grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.oppdaterFradragsgrunnlag(
            fradragOppdatertMedEksterneBeløp,
        ),
        beregning = null,
        simulering = null,
        reguleringstype = reguleringstype,
        sakstype = type,
        aapGrunnlag = eksterntRegulerteBeløp.maptoAap(),
        // regulerteFradragEksternKilde = regulerteFradragEksternKilde. // TODO AUTO-REG-26 - Må lagre
    )

    val utenforToleransegrenser = beregnerUtenforToleransegrenser(this, opprettetRegulering, satsFactory, clock)
    if (utenforToleransegrenser != null) {
        return utenforToleransegrenser.left()
    }

    return opprettetRegulering.right()
}

fun Sak.hentGjeldendeVedtaksdataForRegulering(
    fraOgMedMåned: Måned,
    clock: Clock,
): Either<Sak.KanIkkeRegulere, GjeldendeVedtaksdata> {
    val periode = vedtakstidslinje(
        fraOgMed = fraOgMedMåned,
    ).let { tidslinje ->
        (tidslinje ?: emptyList())
            .filterNot { it.erOpphør() }
            .map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
            .minsteAntallSammenhengendePerioder()
            .ifEmpty {
                log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $fraOgMedMåned")
                return Sak.KanIkkeRegulere.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
            }
    }.also {
        if (it.count() != 1) return Sak.KanIkkeRegulere.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig.left()
    }.single()

    val gjeldendeVedtaksdata = this.hentGjeldendeVedtaksdata(periode = periode, clock = clock).getOrElse { feil ->
        log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere for perioden (${feil.fraOgMed}, ${feil.tilOgMed})")
        return Sak.KanIkkeRegulere.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
    }

    gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent(this.type)
        .onLeft { konsistensproblemer ->
            log.info("Kunne ikke opprette regulering for saksnummer $saksnummer. Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]")
            return Sak.KanIkkeRegulere.MåRevurdere(
                årsak = Sak.KanIkkeRegulere.MåRevurdere.Årsak.INKONSISTENTE_GRUNNLAG_OG_VILKÅR,
            ).left()
        }

    return gjeldendeVedtaksdata.right()
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

private fun beregnerUtenforToleransegrenser(
    sak: Sak,
    regulering: OpprettetRegulering,
    satsFactory: SatsFactory,
    clock: Clock,
): Sak.KanIkkeRegulere.MåRevurdere? {
    if (regulering.vilkårsvurderinger.resultat() is Vurdering.Avslag) {
        return Sak.KanIkkeRegulere.MåRevurdere(
            årsak = Sak.KanIkkeRegulere.MåRevurdere.Årsak.REGULERING_FØRER_TIL_AVSLAG,
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
        val over10prosentEndring = månedsberegning.getSumYtelse() > (gjeldendeUtbetaling * 1.1)
        if (feilutbetaling) {
            Sak.KanIkkeRegulere.MåRevurdere(
                årsak = Sak.KanIkkeRegulere.MåRevurdere.Årsak.REGULERING_BLIR_FEILUTBETALING,
            )
        } else if (over10prosentEndring) {
            Sak.KanIkkeRegulere.MåRevurdere(
                årsak = Sak.KanIkkeRegulere.MåRevurdere.Årsak.REGULERING_ER_OVER_TOLERANSEGRENSE,
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
