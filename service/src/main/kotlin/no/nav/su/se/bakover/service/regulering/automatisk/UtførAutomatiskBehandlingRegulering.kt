import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringUnderBehandling
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.beregnerUtenforToleransegrenser
import no.nav.su.se.bakover.domain.regulering.opprettReguleringForAutomatiskEllerManuellBehandling
import no.nav.su.se.bakover.domain.regulering.toReguleringForLogResultat
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringTestRun
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.harGrunnbeløpSomKanReguleresAutomatisk
import java.time.Clock
import java.util.UUID

internal class UtførAutomatiskBehandlingRegulering(
    private val reguleringService: ReguleringServiceImpl,
    private val reguleringRepo: ReguleringRepo,
    private val satsFactory: SatsFactory,
    private val sessionFactory: SessionFactory,
    private val statistikkService: SakStatistikkService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun utfør(
        saker: List<Either<BleIkkeRegulert, SakTilRegulering>>,
        eksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
        testRun: ReguleringTestRun?,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> {
        return saker.map {
            it.flatMap { sakTilRegulering ->
                log.info("Regulering for saksnummer ${sakTilRegulering.sakInfo.saksnummer}: Starter")
                Either.catch {
                    sakTilRegulering.kjørForSak(
                        satsFactory = satsFactory,
                        sakerMedEksterntRegulerteBeløp = eksterntRegulerteBeløp,
                        testRun = testRun,
                    )
                }.getOrElse {
                    BleIkkeRegulert.KunneIkkeBehandleAutomatisk(
                        feil = KunneIkkeBehandleRegulering.UkjentFeil(it),
                        saksnummer = sakTilRegulering.sakInfo.saksnummer,
                    ).left()
                }
            }
        }
    }

    private fun SakTilRegulering.kjørForSak(
        satsFactory: SatsFactory,
        sakerMedEksterntRegulerteBeløp: List<EksterntRegulerteBeløp>,
        testRun: ReguleringTestRun? = null,
    ): Either<BleIkkeRegulert, ReguleringOppsummering> {
        val (sakId, saksnummer, _, _) = sakInfo

        val regulering = opprettReguleringForAutomatiskEllerManuellBehandling(
            clock = clock,
            alleEksterntRegulerteBeløp = sakerMedEksterntRegulerteBeløp,
        ).getOrElse { feil ->
            log.error("Kan ikke gjennomføre regulering for saksnummer $saksnummer. Saksbehandler må få beskjed om at skal revurderes. Årsak: $feil")
            return BleIkkeRegulert.MåRegulereMedRevurdering(saksnummer, feil).left()
        }

        if (regulering.reguleringstype is Reguleringstype.AUTOMATISK) {
            val utbetalinger = reguleringService.hentUtbetalinger(sakId)

            val skalGjøreToleransesjekk =
                regulering.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.any { it.fradragstype.harGrunnbeløpSomKanReguleresAutomatisk() }
            if (skalGjøreToleransesjekk) {
                val utenforToleransegrenser =
                    beregnerUtenforToleransegrenser(regulering, utbetalinger, satsFactory, clock)
                if (utenforToleransegrenser != null) {
                    log.error("Kan ikke gjennomføre regulering for saksnummer ${sakInfo.saksnummer}. Saksbehandler må få beskjed om at skal revurderes. Årsak: $utenforToleransegrenser")
                    return BleIkkeRegulert.MåRegulereMedRevurdering(sakInfo.saksnummer, utenforToleransegrenser).left()
                }
            }

            return reguleringService.behandleReguleringAutomatisk(
                regulering,
                sakInfo,
                utbetalinger,
                satsFactory,
                isLiveRun = testRun == null,
            )
                .onRight { log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen ble ferdigstilt automatisk") }
                .mapLeft { feil ->
                    BleIkkeRegulert.KunneIkkeBehandleAutomatisk(
                        feil = feil,
                        saksnummer = saksnummer,
                    )
                }
                .fold(
                    ifLeft = { it.left() },
                    ifRight = { it.toReguleringForLogResultat().right() },
                )
        } else {
            log.info("Regulering for saksnummer $saksnummer: Ferdig. Reguleringen må behandles manuelt. ${(regulering.reguleringstype as Reguleringstype.MANUELL).problemer}")
            if (testRun == null || testRun.lagreManuelleUnderDryRun(regulering)) {
                lagreReguleringManuell(sakId, regulering)
            }
            return regulering.toReguleringForLogResultat().right()
        }
    }

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
