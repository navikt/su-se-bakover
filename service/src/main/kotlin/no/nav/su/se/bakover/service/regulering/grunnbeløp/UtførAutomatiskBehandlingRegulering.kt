package no.nav.su.se.bakover.service.regulering.grunnbeløp

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksterntRegulerteBeløp
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

    /**
     * Utfører reguleringsbehandlingen for hver sak som kan reguleres.
     *
     * For hver sak kjøres [kjørForSak]. Ukjente feil under behandlingen fanges og returneres
     * som [BleIkkeRegulert.KunneIkkeBehandleAutomatisk].
     *
     * @param saker sakene som skal behandles, hver med eventuelt utfall fra tidligere steg
     * @param eksterntRegulerteBeløp eksterne regulerte beløp som trengs i behandlingen
     * @param testRun begrensninger for test-/innsynskjøringer, eller null for ordinær kjøring
     * @return ett resultat per sak: enten [BleIkkeRegulert] eller en [ReguleringOppsummering]
     */
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
                        feilmelding = "Ukjent feil oppstod under utførelse av regulering",
                        feil = null,
                        saksnummer = sakTilRegulering.sakInfo.saksnummer,
                    ).left()
                }
            }
        }
    }

    /**
     * Kjører reguleringsbehandlingen for én sak.
     *
     * Oppretter reguleringen med [opprettReguleringForAutomatiskEllerManuellBehandling]. Dersom
     * reguleringen er automatisk, gjøres en toleransesjekk der det er aktuelt (fradrag med
     * grunnbeløp som kan reguleres automatisk) og behandlingen kjøres automatisk. Utenfor
     * toleransegrensene eller andre hindringer returneres som
     * [BleIkkeRegulert.MåRegulereMedRevurdering]. Dersom reguleringen er manuell, lagres den
     * (med mindre det er en dry run som ikke skal lagre manuelt).
     *
     * @return enten [BleIkkeRegulert] eller en [ReguleringOppsummering]
     */
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

    /**
     * Lagrer en manuell regulering og tilhørende statistikkhendelse i én transaksjon.
     *
     * @param sakId identifikator for saken
     * @param regulering den manuelle reguleringen som skal lagres
     * @throws IllegalStateException dersom reguleringen er automatisk, siden automatiske
     *         reguleringer ikke skal lagres før de er ferdigstilt
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
