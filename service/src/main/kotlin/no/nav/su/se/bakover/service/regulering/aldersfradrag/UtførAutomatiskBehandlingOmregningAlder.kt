package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling.OpprettetRegulering
import no.nav.su.se.bakover.domain.regulering.Reguleringsvariant
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.forsøkBeregning
import no.nav.su.se.bakover.domain.regulering.toReguleringForLogResultat
import no.nav.su.se.bakover.domain.regulering.utledReguleringstype
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.service.regulering.AutomatiskTestRunOmregning
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock

internal class UtførAutomatiskBehandlingOmregningAlder(
    private val reguleringService: ReguleringServiceImpl,
    private val satsFactory: SatsFactory,
    private val clock: Clock,
    private val statistikkService: SakStatistikkService,
    private val sessionFactory: SessionFactory,
) {
    fun utfør(
        saker: List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>,
        eksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
        testRun: AutomatiskTestRunOmregning?,
    ): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>> {
        return saker.map {
            it.flatMap { sak ->
                val saksnummer = sak.sakInfo.saksnummer
                val regulering = sak.opprettReguleringForOmregningAlder(
                    clock = clock,
                    alleEksterntRegulerteBeløp = eksterntRegulerteBeløp,
                )
                val utbetalinger = reguleringService.hentUtbetalinger(sak.sakInfo.sakId)

                if (regulering.sjekkOmUnder10Prosent(utbetalinger, satsFactory, clock)) {
                    BleIkkeOmregnetAlder.TrengerIkkeOmregne.ErUnder10ProsentEndring(saksnummer).left()
                } else {
                    // TODO: SOSSTATT lag opprettet event til sakstat her evt senere i løpet med regulering.opprettet timestamp
                    reguleringService.behandleReguleringAutomatisk(
                        regulering,
                        sak.sakInfo,
                        utbetalinger,
                        satsFactory,
                        isLiveRun = testRun == null,
                    ).mapLeft { feil ->
                        BleIkkeOmregnetAlder.KunneIkkeBehandleAutomatisk(
                            feil = feil,
                            saksnummer = saksnummer,
                        )
                    }.map { attestertRegulering ->
                        val attestertReguleringSjekk = attestertRegulering as? ReguleringUnderBehandling.TilAttestering ?: throw IllegalStateException("Expected TilAttestering for omgjøring")
                        if (testRun == null) {
                            sessionFactory.withTransactionContext { tx ->
                                val relId = reguleringService.hentRelatertId(sak.sakInfo.sakId, tx)
                                statistikkService.lagre(
                                    StatistikkEvent.Behandling.ReguleringOmgjøring.Opprettet(regulering, relId),
                                    tx,
                                )
                                statistikkService.lagre(
                                    StatistikkEvent.Behandling.ReguleringOmgjøring.TilAttestering(attestertReguleringSjekk),
                                    tx,
                                )
                            }
                        }

                        attestertRegulering.toReguleringForLogResultat()
                    }
                }
            }
        }
    }

    private fun SakTilRegulering.opprettReguleringForOmregningAlder(
        clock: Clock,
        alleEksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
    ): OpprettetRegulering {
        val eksterntRegulerteBeløp = alleEksterntRegulerteBeløp.singleOrNull {
            it.brukerFnr == sakInfo.fnr
        } ?: throw IllegalStateException(
            "Sak har feil i fradrag fra ekstern kilde. Sak=${sakInfo.saksnummer}",
        )
        val oppdaterteFradrag =
            gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.map {
                if (it.fradragstype == Fradragstype.Alderspensjon) {
                    oppdaterAlderspensjonFradrag(
                        originaltFradrag = it,
                        eksterntRegulerteBeløp = eksterntRegulerteBeløp,
                    )
                } else {
                    it
                }
            }
        val grunnlagsdataOgVilkårsvurderinger =
            gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger
                .oppdaterFradragsgrunnlag(oppdaterteFradrag)

        return OpprettetRegulering.opprett(
            sakInfo = sakInfo,
            reguleringstype = gjeldendeVedtaksdata.utledReguleringstype(),
            reguleringsvariant = Reguleringsvariant.ALDERSFRADRAG,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            clock = clock,
        )
    }

    private fun oppdaterAlderspensjonFradrag(
        originaltFradrag: Fradragsgrunnlag,
        eksterntRegulerteBeløp: EksterntRegulerteBeløp,
    ): Fradragsgrunnlag {
        val fradragTilhører = originaltFradrag.fradrag.tilhører

        val eksterntBeløp = when (fradragTilhører) {
            FradragTilhører.BRUKER -> eksterntRegulerteBeløp.beløpBruker.single()
            FradragTilhører.EPS -> eksterntRegulerteBeløp.beløpEps.single()
        }
        return originaltFradrag.oppdaterBeløpMedEksternRegulering(
            beløp = eksterntBeløp.etterRegulering,
        )
    }

    private fun OpprettetRegulering.sjekkOmUnder10Prosent(
        utbetalinger: Utbetalinger,
        satsFactory: SatsFactory,
        clock: Clock,
    ): Boolean {
        val beregning = forsøkBeregning(
            satsFactory = satsFactory,
            clock = clock,
        ).getOrElse {
            throw RuntimeException("Regulering for saksnummer $saksnummer: Vi klarte ikke å beregne. Underliggende grunn ${it.feil}")
        }

        return beregning.getMånedsberegninger().any { månedsberegning ->
            val eksisterendeBeregning = utbetalinger.hentGjeldendeUtbetaling(månedsberegning.periode.fraOgMed).getOrElse {
                throw IllegalStateException("Fant ikke gjeldende utbetaling for sakId=$sakId under toleransesjekk regulering")
            }.beløp
            val nyBeregning = månedsberegning.getSumYtelse()
            val minimumsøkning = eksisterendeBeregning * 1.1
            val minimumsredusering = eksisterendeBeregning * 0.9
            nyBeregning > minimumsredusering && nyBeregning < minimumsøkning
        }
    }
}
