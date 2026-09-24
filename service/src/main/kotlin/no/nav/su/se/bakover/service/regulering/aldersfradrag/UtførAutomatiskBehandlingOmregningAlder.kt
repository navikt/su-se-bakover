package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.Reguleringsvariant
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.toReguleringForLogResultat
import no.nav.su.se.bakover.domain.regulering.utledReguleringstype
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.service.regulering.AutomatiskTestRun
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock
import java.util.UUID

internal class UtførAutomatiskBehandlingOmregningAlder(
    private val reguleringService: ReguleringServiceImpl,
    private val reguleringRepo: ReguleringRepo,
    private val satsFactory: SatsFactory,
    private val statistikkService: SakStatistikkService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) {
    fun utfør(
        saker: List<Either<BleIkkeOmregnetAlder, SakTilRegulering>>,
        eksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
        testRun: AutomatiskTestRun?,
    ): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>> {
        return saker.map { resultat ->
            resultat.fold(
                ifLeft = { bleIkkeOmregnet ->
                    Either.Left(bleIkkeOmregnet)
                },
                ifRight = { sak ->
                    val (_, saksnummer, _, _) = sak.sakInfo
                    val regulering = sak.opprettReguleringForOmregningAlder(
                        clock = clock,
                        alleEksterntRegulerteBeløp = eksterntRegulerteBeløp,
                    )

                    val utbetalinger = reguleringService.hentUtbetalinger(sak.sakInfo.sakId)
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
                    }.map {
                        OmregningAlderOppsummering(
                            reguleringOppsummering = it.toReguleringForLogResultat(),
                        )
                    }
                },
            )
        }
    }

    private fun SakTilRegulering.opprettReguleringForOmregningAlder(
        clock: Clock,
        alleEksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
    ): ReguleringUnderBehandling.OpprettetRegulering {
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

        return ReguleringUnderBehandling.OpprettetRegulering.opprett(
            sakInfo = sakInfo,
            reguleringstype = gjeldendeVedtaksdata.utledReguleringstype(),
            reguleringsvariant = Reguleringsvariant.ALDERSFRADRAG,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            eksterntRegulerteBeløp = eksterntRegulerteBeløp,
            clock = clock,

        )
    }

    /**
     * Lagrer en manuell behandling fra omregningsflyten og tilhørende statistikkhendelse i én transaksjon.
     *
     * @param sakId identifikator for saken
     * @param regulering den manuelle reguleringen som skal lagres
     * @throws IllegalStateException dersom reguleringen er automatisk
     */

    private fun lagreReguleringManuell(sakId: UUID, regulering: ReguleringUnderBehandling) {
        if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
            throw IllegalStateException("Skal ikke lagre for automatisk regulering før den er ferdigstilt")
        }
        sessionFactory.withTransactionContext { tx ->
            reguleringRepo.lagre(regulering, tx)
            val relatertId = reguleringService.hentRelatertId(sakId, tx)
            statistikkService.lagre(
                StatistikkEvent.Behandling.Regulering.Opprettet(regulering, relatertId),
                tx,
            )
        }
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
}
