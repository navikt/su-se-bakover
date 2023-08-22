package økonomi.application.kvittering

import arrow.core.getOrElse
import arrow.core.toNonEmptyListOrNone
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseJobbRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.kvittering.RåKvitteringHendelse
import økonomi.domain.kvittering.UtbetalingKvitteringRepo
import java.time.Clock

class KnyttKvitteringTilSakOgUtbetalingService(
    private val utbetalingKvitteringRepo: UtbetalingKvitteringRepo,
    private val sakService: SakService,
    private val hendelseJobbRepo: HendelseJobbRepo,
    private val mapRåXmlTilSaksnummerOgUtbetalingId: (String) -> Triple<Saksnummer, UUID30, Kvittering.Utbetalingsstatus>,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun knyttKvitteringerTilSakOgUtbetaling(
        jobbNavn: String,
        correlationId: CorrelationId,
    ) {
        try {
            utbetalingKvitteringRepo.hentUbehandledeKvitteringer(
                jobbNavn = jobbNavn,
            ).forEach { hendelseId ->
                val råKvittering = utbetalingKvitteringRepo.hentRåKvittering(hendelseId) ?: throw IllegalStateException("Vi fikk denne hendelsesIden fra basen, så dette skal ikke kunne skje.")
                val (saksnummer, utbetalingId, utbetalingsstatus) = mapRåXmlTilSaksnummerOgUtbetalingId(råKvittering.originalKvittering)
                val sak = sakService.hentSak(saksnummer).getOrElse {
                    throw RuntimeException("Fant ikke sak med saksnummer $saksnummer")
                }
                val utbetaling = sak.utbetalinger.filter { it.id == utbetalingId }.toNonEmptyListOrNone().getOrElse {
                    log.info("Fant ikke utbetaling med id $utbetalingId på sak $saksnummer. Kanskje den ikke er opprettet enda? Prøver igjen ved neste kjøring.")
                    return
                }.single()
                when (utbetaling) {
                    is Utbetaling.SimulertUtbetaling,
                    is Utbetaling.UtbetalingForSimulering,
                    -> throw IllegalStateException("Utbetalingen skal ikke være i tilstanden ${utbetaling::class.simpleName}")

                    is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                        råKvittering.erAlleredeKvittert(
                            sak = sak,
                            hendelseId = hendelseId,
                            utbetaling = utbetaling,
                            utbetalingsstatus = utbetalingsstatus,
                            utbetalingId = utbetalingId,
                            jobbNavn = jobbNavn,
                            correlationId = correlationId,
                        )
                        return@forEach
                    }

                    is Utbetaling.OversendtUtbetaling.UtenKvittering -> {
                        val hendelsePåSak = råKvittering.tilKvitteringPåSakHendelse(
                            nesteVersjon = sak.versjon.inc(),
                            sakId = sak.id,
                            utbetalingId = utbetalingId,
                            utbetalingsstatus = utbetalingsstatus,
                            clock = clock,
                            correlationId = correlationId,
                        )
                        sessionFactory.withTransactionContext { tx ->
                            utbetalingKvitteringRepo.lagre(hendelsePåSak, tx)
                            hendelseJobbRepo.lagre(
                                hendelseId = råKvittering.hendelseId,
                                jobbNavn = jobbNavn,
                                context = tx,
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Feil skjedde ved oppretting av oppgave for jobb $jobbNavn. originalFeil $e")
        }
    }

    private fun RåKvitteringHendelse.erAlleredeKvittert(
        sak: Sak,
        hendelseId: HendelseId,
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        utbetalingsstatus: Kvittering.Utbetalingsstatus,
        utbetalingId: UUID30,
        jobbNavn: String,
        correlationId: CorrelationId,
    ) {
        val saksnummer = sak.saksnummer
        // jah: Vi forventer i utgangspunktet at dette veldig sjeldent skal inntreffe.
        // Det kan skje dersom oppdrag sender flere kvitteringer på samme utbetalingId, eller dersom vi lagrer en rå hendelse, men ikke klarer acke mot IBM mq.
        if (utbetaling.kvittering.utbetalingsstatus == utbetalingsstatus) {
            log.warn("Utbetaling med id $utbetalingId på sak $saksnummer er allerede kvittert med samme status som før: $utbetalingsstatus. Ignorerer.")
            // Vi lager en jobb, slik at vi ikke prøver igjen.
            hendelseJobbRepo.lagre(
                hendelseId = hendelseId,
                jobbNavn = jobbNavn,
                context = sessionFactory.newSessionContext(),
            )
        } else {
            log.error("Utbetaling med id $utbetalingId på sak $saksnummer er allerede kvittert, men med en annen status. Gammel: ${utbetaling.kvittering.utbetalingsstatus}, ny: $utbetalingsstatus. Oppretter erstatningshendelse.")
            val hendelsePåSak = this.tilKvitteringPåSakHendelse(
                nesteVersjon = sak.versjon.inc(),
                sakId = sak.id,
                utbetalingId = utbetalingId,
                utbetalingsstatus = utbetalingsstatus,
                clock = clock,
                correlationId = correlationId,
            )
            sessionFactory.withTransactionContext { tx ->
                utbetalingKvitteringRepo.lagre(hendelsePåSak, tx)
                hendelseJobbRepo.lagre(
                    hendelseId = hendelseId,
                    jobbNavn = jobbNavn,
                    context = tx,
                )
            }
        }
    }
}
