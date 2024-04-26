package økonomi.application.kvittering

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.kvittering.UtbetalingskvitteringRepo
import økonomi.domain.utbetaling.Utbetaling

/**
 * Tidligere steg:
 * 1. Mottar en kvittering for en utbetaling fra oppdrag - lagrer en hendelse som ikke er knyttet til sak.
 * 2. Knytter kvitteringen til en sak og utbetaling.
 * Dette steget:
 * 3. Ferdigstiller vedtaket, dersom det ikke allerede er ferdigstilt. Dette gjelder bare noen vedtakstyper.
 */
class FerdigstillVedtakEtterMottattKvitteringKonsument(
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val utbetalingKvitteringRepo: UtbetalingskvitteringRepo,
    private val sakService: SakService,
    private val sessionFactory: SessionFactory,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
) : Hendelseskonsument {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override val konsumentId = HendelseskonsumentId("FerdigstillVedtakEtterMottattKvittering")

    fun ferdigstillVedtakEtterMottattKvittering() {
        Either.catch {
            utbetalingKvitteringRepo.hentUprosesserteKnyttetUtbetalingskvitteringTilSak(
                konsumentId = konsumentId,
            ).forEach { hendelseId ->
                prosesserEnHendelse(hendelseId)
            }
        }.onLeft {
            log.error("Feil under kjøring av hendelseskonsument $konsumentId", it)
        }
    }

    private fun prosesserEnHendelse(hendelseId: HendelseId) {
        val hendelse = utbetalingKvitteringRepo.hentKnyttetUtbetalingskvitteringTilSakHendelse(hendelseId)
            ?: throw IllegalStateException("Fant ikke kvittering for hendelseId $hendelseId")

        val sak = sakService.hentSak(hendelse.sakId).getOrElse {
            throw IllegalStateException("Fant ikke sak for sakId $hendelse.sakId")
        }
        val utbetaling = sak.utbetalinger.single { it.id == hendelse.utbetalingId }.let {
            it as? Utbetaling.OversendtUtbetaling.MedKvittering
                ?: throw IllegalStateException("Siden kvitteringen allerede har blitt knyttet til saken, forventet vi en kvittert utbetaling her, men var: ${it::class.simpleName}. Saksnummer: ${sak.saksnummer}, UtbetalingId: ${it.id}")
        }
        sessionFactory.withTransactionContext { tx ->
            // Denne lagrer dokumentet for videre prosessering og prøver lukke oppgave etterpå (får ikke feilmelding dersom sistnevnte feiler)
            ferdigstillVedtakService.ferdigstillVedtakEtterUtbetaling(utbetaling, tx)
                .onLeft {
                    log.error("Feil under ferdigstilling av vedtak etter mottatt kvittering for sak ${sak.saksnummer}, utbetaling ${utbetaling.id} og hendelse $hendelseId. Underliggende feil: $it")
                }.onRight {
                    hendelsekonsumenterRepo.lagre(
                        hendelseId = hendelseId,
                        konsumentId = konsumentId,
                        context = tx,
                    )
                }
        }
    }
}
