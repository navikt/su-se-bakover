package økonomi.application.kvittering

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.kvittering.UtbetalingKvitteringRepo
import økonomi.domain.utbetaling.Utbetaling

/**
 * Tidligere steg:
 * 1. Mottar en kvittering for en utbetaling fra oppdrag - lagrer en hendelse som ikke er knyttet til sak.
 * 2. Knytter kvitteringen til en sak og utbetaling.
 * Dette steget:
 * 3. Ferdigstiller vedtaket, dersom det ikke allerede er ferdigstilt. Dette gjelder bare noen vedtakstyper
 */
class FerdigstillVedtakEtterMottattKvitteringKonsument(
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val utbetalingKvitteringRepo: UtbetalingKvitteringRepo,
    private val sakService: SakService,
    private val sessionFactory: SessionFactory,
    @Suppress("unused") private val oppgaveHendelseRepo: OppgaveHendelseRepo,
) : Hendelseskonsument {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override val konsumentId = HendelseskonsumentId("FerdigstillVedtakEtterMottattKvittering")

    fun ferdigstillVedtakEtterMottattKvittering(
        @Suppress("UNUSED_PARAMETER") correlationId: CorrelationId,
    ) {
        Either.catch {
            utbetalingKvitteringRepo.hentUbehandledeKvitteringerKnyttetMotUtbetaling(
                konsumentId = konsumentId,
            ).forEach { hendelseId ->
                val hendelse = utbetalingKvitteringRepo.hentKvittering(hendelseId)
                    ?: throw IllegalStateException("Fant ikke kvittering for hendelseId $hendelseId")

                val sak = sakService.hentSak(hendelse.sakId).getOrElse {
                    throw IllegalStateException("Fant ikke sak for sakId $hendelse.sakId")
                }
                val utbetaling = sak.utbetalinger.single { it.id == hendelse.utbetalingId }.let {
                    it as? Utbetaling.OversendtUtbetaling.MedKvittering ?: throw IllegalStateException("Siden kvitteringen allerede har blitt knyttet til saken, forventet vi en kvittert utbetaling her, men var: ${it::class.simpleName}. Saksnummer: ${sak.saksnummer}, UtbetalingId: ${it.id}")
                }
                sessionFactory.withTransactionContext {
                    // Denne lagrer dokumentet for videre prosessering og prøver lukke oppgave etterpå (får ikke feilmelding dersom sistnevnte feiler)
                    ferdigstillVedtakService.ferdigstillVedtakEtterUtbetaling(utbetaling)
                        .onLeft {
                            log.error("Feil under ferdigstilling av vedtak etter mottatt kvittering for sak ${sak.saksnummer}, utbetaling ${utbetaling.id} og hendelse $hendelseId. Underliggende feil: $it")
                        }.onRight {
                            // TODO jah: Persister LagretDokumentHendelse ?
                            // TODO jah: Persister LagetOppgaveHendelse ?
                        }
                }
            }
        }.onLeft {
            log.error("Feil under kjøring av hendelseskonsument $konsumentId", it)
        }
    }
}
