package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.domain.Stønadsbehandling
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.supplement.EksternSupplementRegulering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
import vilkår.vurderinger.domain.erGyldigTilstand
import økonomi.domain.simulering.Simulering
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
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

    companion object {
        /**
         * @param clock Brukes kun dersom [opprettet] ikke sendes inn.
         */
        fun opprettRegulering(
            id: ReguleringId = ReguleringId.generer(),
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            clock: Clock,
            opprettet: Tidspunkt = Tidspunkt.now(clock),
            sakstype: Sakstype,
            eksterntRegulerteBeløp: EksterntRegulerteBeløp,
            omregningsfaktor: BigDecimal,
        ): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering.MåRevurdere, OpprettetRegulering> {
            val reguleringstypeVedGenerelleProblemer =
                getReguleringstypeVedGenerelleProblemer(
                    gjeldendeVedtaksdata,
                    saksnummer,
                    sakstype,
                ).getOrElse {
                    return it.left()
                }
            val (reguleringstypeBasertPåFradrag, fradragOppdatertMedEksterneBeløp) = utledReguleringstypeOgOppdaterFradrag(
                fradrag = gjeldendeVedtaksdata.grunnlagsdata.fradragsgrunnlag,
                eksterntRegulerteBeløp = eksterntRegulerteBeløp,
                omregningsfaktor = omregningsfaktor,
            ).getOrElse {
                return it.left()
            }

            // utledning av reguleringstype bør gjøre mer helhetlig, og muligens kun 1 gang. Dette er en midlertidig løsning.
            val reguleringstype = Reguleringstype.utledReguleringsTypeFrom(
                reguleringstype1 = reguleringstypeVedGenerelleProblemer,
                reguleringstype2 = reguleringstypeBasertPåFradrag,
            )

            return OpprettetRegulering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
                fnr = fnr,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.oppdaterFradragsgrunnlag(
                    fradragOppdatertMedEksterneBeløp,
                ),
                beregning = null,
                simulering = null,
                reguleringstype = reguleringstype,
                sakstype = sakstype,
                // regulerteFradragEksternKilde = regulerteFradragEksternKilde. // TODO AUTO-REG-26 - Må lagre
            ).right()
        }

        private fun getReguleringstypeVedGenerelleProblemer(
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            saksnummer: Saksnummer,
            sakstype: Sakstype,
        ): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering.MåRevurdere, Reguleringstype> {
            return gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent(sakstype)
                .fold(
                    { konsistensproblemer ->
                        val message =
                            "Kunne ikke opprette regulering for saksnummer $saksnummer." +
                                " Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]"
                        if (konsistensproblemer.erGyldigTilstand()) {
                            log.info(message)
                        } else {
                            log.error(message)
                        }
                        return Sak.KunneIkkeOppretteEllerOppdatereRegulering.MåRevurdere.left()
                    },
                    {
                        gjeldendeVedtaksdata.utledReguleringstype().right()
                    },
                )
        }
    }
}

fun Sak.opprettReguleringForAutomatiskEllerManuellBehandling(
    clock: Clock,
    vedtaksdata: GjeldendeVedtaksdata,
    eksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
    omregningsfaktor: BigDecimal,
): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering.MåRevurdere, OpprettetRegulering> {
    if (reguleringer.filterIsInstance<ReguleringUnderBehandling>().isNotEmpty()) {
        throw IllegalStateException("Skal ikke kunne finnes åpne reguleringer på dette stadiet. Skal valideres i tidligere steg")
    }
    return Regulering.opprettRegulering(
        sakId = id,
        saksnummer = saksnummer,
        fnr = fnr,
        gjeldendeVedtaksdata = vedtaksdata,
        clock = clock,
        sakstype = type,
        eksterntRegulerteBeløp = eksterntRegulerteBeløp.singleOrNull { it.brukerFnr == fnr }
            ?: throw IllegalStateException("Sak har feil i fradrag fra ekstern kilde. Sak=$saksnummer"),
        omregningsfaktor = omregningsfaktor,
    )
}

fun Sak.hentGjeldendeVedtaksdataForRegulering(
    fraOgMedMåned: Måned,
    clock: Clock,
): Either<Sak.KunneIkkeOppretteEllerOppdatereRegulering, GjeldendeVedtaksdata> {
    val periode = vedtakstidslinje(
        fraOgMed = fraOgMedMåned,
    ).let { tidslinje ->
        (tidslinje ?: emptyList())
            .filterNot { it.erOpphør() }
            .map { vedtakUtenOpphør -> vedtakUtenOpphør.periode }
            .minsteAntallSammenhengendePerioder()
            .ifEmpty {
                log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere fra og med $fraOgMedMåned")
                return Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
            }
    }.also {
        if (it.count() != 1) return Sak.KunneIkkeOppretteEllerOppdatereRegulering.StøtterIkkeVedtaktidslinjeSomIkkeErKontinuerlig.left()
    }.single()

    val gjeldendeVedtaksdata = this.hentGjeldendeVedtaksdata(periode = periode, clock = clock).getOrElse { feil ->
        log.info("Kunne ikke opprette eller oppdatere regulering for saksnummer $saksnummer. Underliggende feil: Har ingen vedtak å regulere for perioden (${feil.fraOgMed}, ${feil.tilOgMed})")
        return Sak.KunneIkkeOppretteEllerOppdatereRegulering.FinnesIngenVedtakSomKanRevurderesForValgtPeriode.left()
    }
    return gjeldendeVedtaksdata.right()
}
