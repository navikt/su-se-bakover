package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.JmsConfig
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.jobs.infrastructure.RunCheckFactory
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.next
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.web.services.SendPåminnelseNyStønadsperiodeJob
import no.nav.su.se.bakover.web.services.avstemming.GrensesnittsavstemingJob
import no.nav.su.se.bakover.web.services.avstemming.KonsistensavstemmingJob
import no.nav.su.se.bakover.web.services.dokument.DistribuerDokumentJob
import no.nav.su.se.bakover.web.services.klage.klageinstans.KlageinstanshendelseConsumer
import no.nav.su.se.bakover.web.services.klage.klageinstans.KlageinstanshendelseJob
import no.nav.su.se.bakover.web.services.kontrollsamtale.KontrollsamtaleinnkallingJob
import no.nav.su.se.bakover.web.services.kontrollsamtale.StansYtelseVedManglendeOppmøteKontrollsamtaleJob
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseConsumer
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseOppgaveJob
import no.nav.su.se.bakover.web.services.tilbakekreving.LokalMottaKravgrunnlagJob
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingIbmMqConsumer
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.LokalKvitteringJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.LokalKvitteringService
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringIbmMqConsumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date

internal fun startJobberOgConsumers(
    services: Services,
    clients: Clients,
    databaseRepos: DatabaseRepos,
    applicationConfig: ApplicationConfig,
    jmsConfig: JmsConfig,
    clock: Clock,
    consumers: Consumers,
) {
    val personhendelseService = PersonhendelseService(
        sakRepo = databaseRepos.sak,
        personhendelseRepo = databaseRepos.personhendelseRepo,
        oppgaveServiceImpl = services.oppgave,
        personService = services.person,
        clock = clock,
    )
    val runCheckFactory = RunCheckFactory(
        leaderPodLookup = clients.leaderPodLookup,
        applicationConfig = applicationConfig,
        clock = clock,
        toggleService = services.toggles,

    )
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Nais) {
        // Prøver å time starten på jobbene slik at de ikke går i beina på hverandre.
        val initialDelay = object {
            var initialDelay: Duration = Duration.ofMinutes(5)
            fun next(): Duration {
                return initialDelay.also {
                    initialDelay.plus(Duration.ofSeconds(30))
                }
            }
        }
        val isProd = applicationConfig.naisCluster == ApplicationConfig.NaisCluster.Prod
        UtbetalingKvitteringIbmMqConsumer(
            kvitteringQueueName = applicationConfig.oppdrag.utbetaling.mqReplyTo,
            globalJmsContext = jmsConfig.jmsContext,
            kvitteringConsumer = consumers.utbetalingKvitteringConsumer,
        )
        PersonhendelseConsumer(
            consumer = KafkaConsumer(applicationConfig.kafkaConfig.consumerCfg.kafkaConfig),
            personhendelseService = personhendelseService,
        )
        KlageinstanshendelseConsumer(
            consumer = KafkaConsumer(applicationConfig.kabalKafkaConfig.kafkaConfig),
            klageinstanshendelseService = services.klageinstanshendelseService,
            clock = clock,
        )

        DistribuerDokumentJob(
            brevService = services.brev,
            initialDelay = initialDelay.next(),
            periode = Duration.of(15, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
        ).schedule()

        GrensesnittsavstemingJob(
            avstemmingService = services.avstemming,
            starttidspunkt = ZonedDateTime.now(zoneIdOslo).next(LocalTime.of(1, 0, 0)),
            periode = Duration.of(1, ChronoUnit.DAYS),
            runCheckFactory = runCheckFactory,
        ).schedule()

        KonsistensavstemmingJob(
            avstemmingService = services.avstemming,
            kjøreplan = if (isProd) {
                setOf(
                    28.oktober(2022),
                    21.november(2022),
                    5.januar(2023),
                    30.januar(2023),
                    27.februar(2023),
                    28.mars(2023),
                    25.april(2023),
                    30.mai(2023),
                    29.juni(2023),
                    28.juli(2023),
                    30.august(2023),
                    29.september(2023),
                    30.oktober(2023),
                    22.november(2023),
                )
            } else {
                emptySet()
            },
            initialDelay = initialDelay.next(),
            periode = Duration.of(4, ChronoUnit.HOURS),
            clock = clock,
            runCheckFactory = runCheckFactory,
        ).schedule()

        KlageinstanshendelseJob(
            klageinstanshendelseService = services.klageinstanshendelseService,
            initialDelay = initialDelay.next(),
            periode = Duration.of(15, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
        ).schedule()

        PersonhendelseOppgaveJob(
            personhendelseService = personhendelseService,
            initialDelay = initialDelay.next(),
            periode = if (isProd) {
                Duration.of(1, ChronoUnit.DAYS)
            } else {
                Duration.of(15, ChronoUnit.MINUTES)
            },
            runCheckFactory = runCheckFactory,
        ).schedule()

        KontrollsamtaleinnkallingJob(
            kontrollsamtaleService = services.kontrollsamtale,
            starttidspunkt = if (isProd) {
                ZonedDateTime.now(zoneIdOslo).next(LocalTime.of(7, 0, 0))
            } else {
                Date.from(Instant.now(clock))
            },
            periode = if (isProd) {
                Duration.of(1, ChronoUnit.DAYS)
            } else {
                Duration.of(15, ChronoUnit.MINUTES)
            },
            runCheckFactory = runCheckFactory,
        ).schedule()

        TilbakekrevingIbmMqConsumer(
            queueName = applicationConfig.oppdrag.tilbakekreving.mq.mottak,
            globalJmsContext = jmsConfig.jmsContext,
            tilbakekrevingConsumer = consumers.tilbakekrevingConsumer,
        )

        TilbakekrevingJob(
            tilbakekrevingService = services.tilbakekrevingService,
            initialDelay = initialDelay.next(),
            intervall = Duration.of(15, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
        ).schedule()

        SendPåminnelseNyStønadsperiodeJob(
            intervall = Duration.of(4, ChronoUnit.HOURS),
            initialDelay = initialDelay.next(),
            sendPåminnelseService = services.sendPåminnelserOmNyStønadsperiodeService,
            runCheckFactory = runCheckFactory,
        ).schedule()

        StansYtelseVedManglendeOppmøteKontrollsamtaleJob(
            intervall = Duration.of(2, ChronoUnit.HOURS),
            initialDelay = initialDelay.next(),
            service = services.utløptFristForKontrollsamtaleService,
            clock = clock,
            runCheckFactory = runCheckFactory,
        ).schedule()
    } else if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        // Prøver å time starten på de lokale jobbene slik at heller ikke de går i beina på hverandre.
        val initialDelay = object {
            var initialDelay: Duration = Duration.ZERO
            fun next(): Duration {
                return initialDelay.also {
                    initialDelay.plus(Duration.ofSeconds(5))
                }
            }
        }
        LokalKvitteringJob(
            lokalKvitteringService = LokalKvitteringService(
                utbetalingRepo = databaseRepos.utbetaling,
                utbetalingKvitteringConsumer = consumers.utbetalingKvitteringConsumer,
            ),
            initialDelay = initialDelay.next(),
            periode = Duration.ofSeconds(10),
        ).schedule()

        DistribuerDokumentJob(
            brevService = services.brev,
            initialDelay = initialDelay.next(),
            periode = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
        ).schedule()

        GrensesnittsavstemingJob(
            avstemmingService = services.avstemming,
            starttidspunkt = Date.from(Instant.now(clock).plusSeconds(initialDelay.next().toSeconds())),
            periode = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
        ).schedule()

        KonsistensavstemmingJob(
            avstemmingService = services.avstemming,
            kjøreplan = emptySet(),
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(5),
            clock = clock,
            runCheckFactory = runCheckFactory,
        ).schedule()

        KlageinstanshendelseJob(
            klageinstanshendelseService = services.klageinstanshendelseService,
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
        ).schedule()

        PersonhendelseOppgaveJob(
            personhendelseService = personhendelseService,
            periode = Duration.ofMinutes(5),
            initialDelay = initialDelay.next(),
            runCheckFactory = runCheckFactory,
        ).schedule()

        KontrollsamtaleinnkallingJob(
            kontrollsamtaleService = services.kontrollsamtale,
            starttidspunkt = Date.from(Instant.now(clock).plusSeconds(initialDelay.next().toSeconds())),
            periode = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
        ).schedule()

        LokalMottaKravgrunnlagJob(
            tilbakekrevingConsumer = consumers.tilbakekrevingConsumer,
            tilbakekrevingService = services.tilbakekrevingService,
            vedtakService = services.vedtakService,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
        ).schedule()

        TilbakekrevingJob(
            tilbakekrevingService = services.tilbakekrevingService,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
        ).schedule()

        SendPåminnelseNyStønadsperiodeJob(
            intervall = Duration.of(1, ChronoUnit.MINUTES),
            initialDelay = initialDelay.next(),
            sendPåminnelseService = services.sendPåminnelserOmNyStønadsperiodeService,
            runCheckFactory = runCheckFactory,
        ).schedule()

        StansYtelseVedManglendeOppmøteKontrollsamtaleJob(
            intervall = Duration.of(1, ChronoUnit.MINUTES),
            initialDelay = initialDelay.next(),
            service = services.utløptFristForKontrollsamtaleService,
            clock = clock,
            runCheckFactory = runCheckFactory,
        ).schedule()
    }
}
