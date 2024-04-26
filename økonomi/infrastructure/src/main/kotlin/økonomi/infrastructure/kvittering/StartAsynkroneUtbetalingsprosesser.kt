package økonomi.infrastructure.kvittering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringIbmMqConsumer
import økonomi.infrastructure.kvittering.consumer.lokal.LokalKvitteringJob
import økonomi.infrastructure.kvittering.consumer.lokal.LokalKvitteringService
import økonomi.infrastructure.kvittering.job.KvitteringshendelseJob
import java.time.Duration

/**
 * Starter asynkrone utbetalingsprosesser som:
 * - Lytter på kø for kvitteringer fra oppdrag
 * - Jobb for å knytte kvittering til sak og utbetaling
 * - TODO jah: Legg inn avstemming-consumer her
 */
fun startAsynkroneUtbetalingsprosesser(
    utbetalingskvitteringKomponenter: UtbetalingskvitteringKomponenter,
    oppdragConfig: ApplicationConfig.OppdragConfig,
    jmsConfig: JmsConfig,
    utbetalingRepo: UtbetalingRepo,
    runCheckFactory: RunCheckFactory,
    initalDelay: () -> Duration,
    runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
) {
    if (runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Nais) {
        // Har en init som starter consumern.
        UtbetalingKvitteringIbmMqConsumer(
            kvitteringQueueName = oppdragConfig.utbetaling.mqReplyTo,
            globalJmsContext = jmsConfig.jmsContext,
            råKvitteringService = utbetalingskvitteringKomponenter.råKvitteringService,
        )

        KvitteringshendelseJob(
            knyttKvitteringTilSakOgUtbetalingService = utbetalingskvitteringKomponenter.knyttKvitteringTilSakOgUtbetalingService,
            initialDelay = initalDelay(),
            intervall = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
            ferdigstillVedtakEtterMottattKvitteringKonsument = utbetalingskvitteringKomponenter.ferdigstillVedtakEtterMottattKvitteringKonsument,
        ).schedule()
    } else {
        LokalKvitteringJob(
            lokalKvitteringService = LokalKvitteringService(
                utbetalingRepo = utbetalingRepo,
                råKvitteringService = utbetalingskvitteringKomponenter.råKvitteringService,
                knyttKvitteringTilSakOgUtbetalingService = utbetalingskvitteringKomponenter.knyttKvitteringTilSakOgUtbetalingService,
                ferdigstillVedtakEtterMottattKvitteringKonsument = utbetalingskvitteringKomponenter.ferdigstillVedtakEtterMottattKvitteringKonsument,
            ),
            intervall = Duration.ofSeconds(5),
            initialDelay = initalDelay(),
        ).schedule()
    }
}

val xmlMapperForUtbetalingskvittering: XmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
    registerModule(KotlinModule.Builder().build())
    registerModule(JavaTimeModule())
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
