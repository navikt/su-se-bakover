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
import no.nav.su.se.bakover.hendelse.domain.HendelseActionRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import økonomi.application.kvittering.KnyttKvitteringTilSakOgUtbetalingService
import økonomi.application.kvittering.RåKvitteringService
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringIbmMqConsumerV2
import økonomi.infrastructure.kvittering.consumer.kvitteringXmlTilSaksnummerOgUtbetalingId
import økonomi.infrastructure.kvittering.job.KnyttKvitteringTilSakOgUtbetalingJob
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
    hendelseActionRepo: HendelseActionRepo,
    hendelseRepo: HendelsePostgresRepo,
    dbMetrics: DbMetrics,
    utbetalingService: UtbetalingService,
    // TODO jah: Lag en jobb+service for å ferdigstille vedtak med utbetaling+kvittering
    @Suppress("UNUSED_PARAMETER") ferdigstillVedtakService: FerdigstillVedtakService,
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
        hendelseActionRepo = hendelseActionRepo,
        dbMetrics = dbMetrics,
    )
    val knyttKvitteringTilSakOgUtbetalingService =
        KnyttKvitteringTilSakOgUtbetalingService(
            utbetalingKvitteringRepo = utbetalingKvitteringRepo,
            sakService = sakService,
            hendelseActionRepo = hendelseActionRepo,
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
    KnyttKvitteringTilSakOgUtbetalingJob(
        service = knyttKvitteringTilSakOgUtbetalingService,
        initialDelay = initalDelay(),
        intervall = Duration.ofMinutes(5),
        runCheckFactory = runCheckFactory,
    )
}
