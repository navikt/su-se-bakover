package økonomi.infrastructure.kvittering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import økonomi.application.kvittering.FerdigstillVedtakEtterMottattKvitteringKonsument
import økonomi.application.kvittering.KnyttKvitteringTilSakOgUtbetalingKonsument
import økonomi.application.kvittering.RåKvitteringService
import økonomi.application.utbetaling.UtbetalingService
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringIbmMqConsumerV2
import økonomi.infrastructure.kvittering.consumer.kvitteringXmlTilSaksnummerOgUtbetalingId
import økonomi.infrastructure.kvittering.job.KvitteringshendelseJob
import økonomi.infrastructure.kvittering.persistence.UtbetalingKvitteringPostgresRepo
import java.time.Clock
import java.time.Duration

/**
 * TODO jah: Denne er ikke i bruk enda. Skal erstatte UtbetalingKvitteringIbmMqConsumer.
 *
 * Starter asynkrone utbetalingsprosesser som:
 * - Lytter på kø for kvitteringer fra oppdrag
 * - Jobb for å knytte kvittering til sak og utbetaling
 * - TODO jah: Legg inn avstemming-consumer her
 */
fun startAsynkroneUtbetalingsprosesser(
    oppdragConfig: ApplicationConfig.OppdragConfig,
    jmsConfig: JmsConfig,
    sakService: SakService,
    sessionFactory: SessionFactory,
    clock: Clock,
    hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    hendelseRepo: HendelsePostgresRepo,
    oppgaveHendelseRepo: OppgaveHendelseRepo,
    dbMetrics: DbMetrics,
    utbetalingService: UtbetalingService,
    // TODO jah: Lag en jobb+service for å ferdigstille vedtak med utbetaling+kvittering
    ferdigstillVedtakService: FerdigstillVedtakService,
    runCheckFactory: RunCheckFactory,
    initalDelay: () -> Duration,
) {
    val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
    val utbetalingKvitteringRepo = UtbetalingKvitteringPostgresRepo(
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        dbMetrics = dbMetrics,
    )
    val knyttKvitteringTilSakOgUtbetalingService =
        KnyttKvitteringTilSakOgUtbetalingKonsument(
            utbetalingKvitteringRepo = utbetalingKvitteringRepo,
            sakService = sakService,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            mapRåXmlTilSaksnummerOgUtbetalingId = kvitteringXmlTilSaksnummerOgUtbetalingId(
                xmlMapper = xmlMapper,
            ),
            clock = clock,
            sessionFactory = sessionFactory,
            utbetalingService = utbetalingService,
        )

    val råKvitteringService = RåKvitteringService(
        utbetalingKvitteringRepo = utbetalingKvitteringRepo,
        clock = clock,
    )
    // Har en init som starter consumern.
    UtbetalingKvitteringIbmMqConsumerV2(
        kvitteringQueueName = oppdragConfig.utbetaling.mqReplyTo,
        globalJmsContext = jmsConfig.jmsContext,
        råKvitteringService = råKvitteringService,
    )
    KvitteringshendelseJob(
        knyttKvitteringTilSakOgUtbetalingService = knyttKvitteringTilSakOgUtbetalingService,
        initialDelay = initalDelay(),
        intervall = Duration.ofMinutes(5),
        runCheckFactory = runCheckFactory,
        ferdigstillVedtakEtterMottattKvitteringKonsument = FerdigstillVedtakEtterMottattKvitteringKonsument(
            ferdigstillVedtakService = ferdigstillVedtakService,
            utbetalingKvitteringRepo = utbetalingKvitteringRepo,
            sakService = sakService,
            sessionFactory = sessionFactory,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
        ),
    )
}
