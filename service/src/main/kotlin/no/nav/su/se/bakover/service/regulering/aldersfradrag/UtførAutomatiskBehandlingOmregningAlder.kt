package no.nav.su.se.bakover.service.regulering.aldersfradrag

import BleIkkeOmregnetAlder
import OmregningAlderOppsummering
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.beregnerUtenforToleransegrenser
import no.nav.su.se.bakover.domain.regulering.opprettReguleringForAutomatiskEllerManuellBehandling
import no.nav.su.se.bakover.domain.regulering.toReguleringForLogResultat
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.harGrunnbeløpSomKanReguleresAutomatisk
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
        saker: List<Either<BleIkkeRegulert, SakTilRegulering>>,
        eksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
    ): List<Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>> {
        return saker.map { resultat ->
            resultat.fold(
                ifLeft = { bleIkkeRegulert ->
                    Either.Left(
                        BleIkkeOmregnetAlder.FraReguleringsflyt(
                            bleIkkeRegulert,
                        ),
                    )
                },
                ifRight = { sak ->
                    val (_, saksnummer, _, _) = sak.sakInfo
                    val regulering = sak.opprettReguleringForAutomatiskEllerManuellBehandling(
                        clock = clock,
                        alleEksterntRegulerteBeløp = eksterntRegulerteBeløp,
                    ).getOrElse { feil ->
                        return@fold Either.Left(
                            BleIkkeOmregnetAlder.FraReguleringsflyt(
                                BleIkkeRegulert.MåRegulereMedRevurdering(
                                    saksnummer = saksnummer,
                                    årsak = feil,
                                ),
                            ),
                        )
                    }
                    if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
                        val utbetalinger = reguleringService.hentUtbetalinger(sak.sakInfo.sakId)
                        val skalGjøreToleransesjekk = regulering.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.any {
                            it.fradragstype.harGrunnbeløpSomKanReguleresAutomatisk()
                        }
                        if (skalGjøreToleransesjekk) {
                            val utenforToleransegrenser =
                                beregnerUtenforToleransegrenser(
                                    regulering,
                                    utbetalinger,
                                    satsFactory,
                                    clock,
                                )
                            if (utenforToleransegrenser != null) {
                                return@fold Either.Left(
                                    BleIkkeOmregnetAlder.FraReguleringsflyt(
                                        BleIkkeRegulert.MåRegulereMedRevurdering(
                                            saksnummer = saksnummer,
                                            årsak = utenforToleransegrenser,
                                        ),
                                    ),
                                )
                            }
                        }
                        reguleringService.behandleReguleringAutomatisk(
                            regulering,
                            sak.sakInfo,
                            utbetalinger,
                            satsFactory,
                            isLiveRun = true,
                        ).mapLeft { feil ->
                            BleIkkeOmregnetAlder.FraReguleringsflyt(
                                BleIkkeRegulert.KunneIkkeBehandleAutomatisk(
                                    feil = feil,
                                    saksnummer = saksnummer,
                                ),
                            )
                        }.map {
                            OmregningAlderOppsummering(
                                reguleringOppsummering = it.toReguleringForLogResultat(),
                            )
                        }
                    } else {
                        lagreReguleringManuell(sak.sakInfo.sakId, regulering)
                        OmregningAlderOppsummering(
                            reguleringOppsummering = regulering.toReguleringForLogResultat(),
                        ).right()
                    }
                },
            )
        }
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
}
