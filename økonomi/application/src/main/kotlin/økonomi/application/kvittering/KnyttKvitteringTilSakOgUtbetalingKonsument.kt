package økonomi.application.kvittering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.flattenOrAccumulate
import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.toNonEmptyListOrNone
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.kvittering.RåUtbetalingskvitteringhendelse
import økonomi.domain.kvittering.UtbetalingskvitteringRepo
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock
import java.util.UUID

class KnyttKvitteringTilSakOgUtbetalingKonsument(
    private val utbetalingKvitteringRepo: UtbetalingskvitteringRepo,
    private val sakService: SakService,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val mapRåXmlTilSaksnummerOgUtbetalingId: (String) -> Triple<Saksnummer, UUID30, Kvittering.Utbetalingsstatus>,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
    private val utbetalingService: UtbetalingService,
) : Hendelseskonsument {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override val konsumentId = HendelseskonsumentId("KnyttKvitteringTilSakOgUtbetaling")

    fun knyttKvitteringerTilSakOgUtbetaling(
        correlationId: CorrelationId,
    ): Either<Nel<Throwable>, Unit> {
        return Either.catch {
            utbetalingKvitteringRepo.hentUprosesserteMottattUtbetalingskvittering(konsumentId = konsumentId)
                .map { hendelseId ->
                    prosesserEnHendelse(hendelseId = hendelseId, correlationId = correlationId)
                }
        }.mapLeft {
            log.error(
                "Kunne ikke prosessere utbetalingskvittering: Det ble kastet en exception ved hentUprosesserteMottattUtbetalingskvittering for konsument $konsumentId",
                it,
            )
            nonEmptyListOf(it)
        }.flatMap {
            it.flattenOrAccumulate().map { }
        }
    }

    /**
     * TODO jah: Bytt fra exception kontrollflyt til lefts. Vil også være enklere og teste.
     */
    private fun prosesserEnHendelse(
        hendelseId: HendelseId,
        correlationId: CorrelationId,
    ): Either<Throwable, Unit> {
        return Either.catch {
            val råKvittering = utbetalingKvitteringRepo.hentRåUtbetalingskvitteringhendelse(hendelseId) ?: run {
                throw IllegalStateException("Kunne ikke prosessere utbetalingskvittering: hentUprosesserteMottattUtbetalingskvittering returnerte hendelseId $hendelseId fra basen, men hentRåUtbetalingskvitteringhendelse fant den ikke. Denne vil prøves på nytt.")
            }
            val (saksnummer, utbetalingId, utbetalingsstatus) = mapRåXmlTilSaksnummerOgUtbetalingId(råKvittering.originalKvittering)
            val sak = sakService.hentSak(saksnummer).getOrElse {
                throw IllegalStateException("Kunne ikke prosessere utbetalingskvittering: Fant ikke saksnummer $saksnummer. Denne vil prøves på nytt.")
            }
            val utbetaling = sak.utbetalinger.filter { it.id == utbetalingId }.toNonEmptyListOrNone().getOrElse {
                throw IllegalStateException("Kunne ikke prosessere utbetalingskvittering: Fant ikke utbetaling med id $utbetalingId på sak $saksnummer. Kanskje den ikke er opprettet enda? Prøver igjen ved neste kjøring.")
            }.single()

            val sakId = sak.id
            if (utbetalingKvitteringRepo.hentUtbetalingskvitteringerPåSakHendelser(sak.id).any { it.tidligereHendelseId == hendelseId }) {
                log.info("Vi har allerede knyttet den rå kvitteringen til en sak. Denne vil ikke prøves på nytt. Hendelse $hendelseId, sak ${sak.id}")
                hendelsekonsumenterRepo.lagre(
                    hendelseId = hendelseId,
                    konsumentId = konsumentId,
                )
                return Unit.right()
            }
            when (utbetaling) {
                is Utbetaling.SimulertUtbetaling,
                is Utbetaling.UtbetalingForSimulering,
                -> throw IllegalStateException("Kunne ikke prosessere utbetalingskvittering: Utbetalingen skal ikke være i tilstanden ${utbetaling::class.simpleName}. Denne vil prøves på nytt.")

                is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                    råKvittering.prosesserAlleredeKvittertUtbetaling(
                        sak = sak,
                        hendelseId = hendelseId,
                        utbetaling = utbetaling,
                        utbetalingsstatus = utbetalingsstatus,
                        utbetalingId = utbetalingId,
                        konsumentId = konsumentId,
                        correlationId = correlationId,
                    )
                }

                is Utbetaling.OversendtUtbetaling.UtenKvittering -> {
                    prosesserUtbetalingUtenKvittering(
                        råKvittering = råKvittering,
                        sak = sak,
                        sakId = sakId,
                        utbetalingId = utbetalingId,
                        utbetalingsstatus = utbetalingsstatus,
                        correlationId = correlationId,
                    )
                }
            }
        }.onLeft {
            log.error(
                "Kunne ikke prosessere utbetalingskvittering: Det ble kastet en exception for hendelsen $hendelseId",
                it,
            )
        }
    }

    private fun prosesserUtbetalingUtenKvittering(
        råKvittering: RåUtbetalingskvitteringhendelse,
        sak: Sak,
        sakId: UUID,
        utbetalingId: UUID30,
        utbetalingsstatus: Kvittering.Utbetalingsstatus,
        correlationId: CorrelationId,
    ) {
        val hendelsePåSak = råKvittering.tilKvitteringPåSakHendelse(
            nesteVersjon = sak.versjon.inc(),
            sakId = sakId,
            utbetalingId = utbetalingId,
            utbetalingsstatus = utbetalingsstatus,
            clock = clock,
            tidligereHendelseId = råKvittering.hendelseId,
        )
        sessionFactory.withTransactionContext { tx ->
            utbetalingKvitteringRepo.lagreUtbetalingskvitteringPåSakHendelse(
                hendelse = hendelsePåSak,
                meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                sessionContext = tx,
            )
            hendelsekonsumenterRepo.lagre(
                hendelseId = råKvittering.hendelseId,
                konsumentId = konsumentId,
                context = tx,
            )
            utbetalingService.oppdaterMedKvittering(
                utbetalingId = utbetalingId,
                kvittering = hendelsePåSak.kvittering,
                sessionContext = tx,
            )
        }
        Unit
    }

    private fun RåUtbetalingskvitteringhendelse.prosesserAlleredeKvittertUtbetaling(
        sak: Sak,
        hendelseId: HendelseId,
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        utbetalingsstatus: Kvittering.Utbetalingsstatus,
        utbetalingId: UUID30,
        konsumentId: HendelseskonsumentId,
        correlationId: CorrelationId,
    ) {
        val saksnummer = sak.saksnummer
        // jah: Vi forventer i utgangspunktet at dette veldig sjeldent skal inntreffe.
        // Det kan skje dersom oppdrag sender flere kvitteringer på samme utbetalingId, eller dersom vi lagrer en rå hendelse, men ikke klarer acke mot IBM mq. Vi gjør ingen dedup i rå kvittering steget.
        if (utbetaling.kvittering.originalKvittering == originalKvittering) {
            log.warn("Utbetaling med id $utbetalingId på sak $saksnummer er allerede kvittert med samme kvittering. Vi ignorerer denne og setter den som utført.")
            hendelsekonsumenterRepo.lagre(
                hendelseId = hendelseId,
                konsumentId = konsumentId,
                context = sessionFactory.newSessionContext(),
            )
        } else {
            log.error("Utbetaling med id $utbetalingId på sak $saksnummer er allerede kvittert, men med andre data. Oppretter erstatningshendelse.")
            val hendelsePåSak = this.tilKvitteringPåSakHendelse(
                nesteVersjon = sak.versjon.inc(),
                sakId = sak.id,
                utbetalingId = utbetalingId,
                utbetalingsstatus = utbetalingsstatus,
                clock = clock,
                tidligereHendelseId = this.hendelseId,
            )
            sessionFactory.withTransactionContext { tx ->
                utbetalingKvitteringRepo.lagreUtbetalingskvitteringPåSakHendelse(
                    hendelsePåSak,
                    DefaultHendelseMetadata.fraCorrelationId(correlationId),
                    tx,
                )
                hendelsekonsumenterRepo.lagre(
                    hendelseId = hendelseId,
                    konsumentId = konsumentId,
                    context = tx,
                )
                utbetalingService.oppdaterMedKvittering(
                    utbetalingId = utbetalingId,
                    kvittering = hendelsePåSak.kvittering,
                    sessionContext = tx,
                )
            }
        }
    }
}
