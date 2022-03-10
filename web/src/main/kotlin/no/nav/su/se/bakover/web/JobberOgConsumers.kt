package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.JmsConfig
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
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
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.services.avstemming.GrensesnittsavstemingJob
import no.nav.su.se.bakover.web.services.avstemming.KonsistensavstemmingJob
import no.nav.su.se.bakover.web.services.dokument.DistribuerDokumentJob
import no.nav.su.se.bakover.web.services.klage.klageinstans.KlageinstanshendelseConsumer
import no.nav.su.se.bakover.web.services.klage.klageinstans.KlageinstanshendelseJob
import no.nav.su.se.bakover.web.services.kontrollsamtale.KontrollsamtaleinnkallingJob
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseConsumer
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseOppgaveJob
import no.nav.su.se.bakover.web.services.tilbakekreving.LokalMottaKravgrunnlagJob
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingConsumer
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingIbmMqConsumer
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.LokalKvitteringJob
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.LokalKvitteringService
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringIbmMqConsumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date

fun startJobberOgConsumers(
    services: Services,
    clients: Clients,
    databaseRepos: DatabaseRepos,
    applicationConfig: ApplicationConfig,
    jmsConfig: JmsConfig,
    clock: Clock,
) {
    val utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
        utbetalingService = services.utbetaling,
        ferdigstillVedtakService = services.ferdigstillVedtak,
        clock = clock,
    )
    val personhendelseService = PersonhendelseService(
        sakRepo = databaseRepos.sak,
        personhendelseRepo = databaseRepos.personhendelseRepo,
        oppgaveServiceImpl = services.oppgave,
        personService = services.person,
        clock = clock,
    )
    val tilbakekrevingConsumer = TilbakekrevingConsumer(
        tilbakekrevingService = services.tilbakekrevingService,
        revurderingService = services.revurdering,
        clock = clock,
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
            kvitteringConsumer = utbetalingKvitteringConsumer,
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
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = initialDelay.next(),
            periode = Duration.of(15, ChronoUnit.MINUTES),
        ).schedule()

        GrensesnittsavstemingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
            starttidspunkt = ZonedDateTime.now(zoneIdOslo).next(LocalTime.of(1, 0, 0)),
            periode = Duration.of(1, ChronoUnit.DAYS),
        ).schedule()

        KonsistensavstemmingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
            kjøreplan = if (isProd) setOf(
                22.november(2021),
                5.januar(2022),
                28.januar(2022),
                25.februar(2022),
                25.mars(2022),
                26.april(2022),
                27.mai(2022),
                29.juni(2022),
                29.juli(2022),
                30.august(2022),
                29.september(2022),
                28.oktober(2022),
                21.november(2022),
            ) else emptySet(),
            initialDelay = initialDelay.next(),
            periode = Duration.of(4, ChronoUnit.HOURS),
            clock = clock,
        ).schedule()

        KlageinstanshendelseJob(
            klageinstanshendelseService = services.klageinstanshendelseService,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = initialDelay.next(),
            periode = Duration.of(15, ChronoUnit.MINUTES),
        ).schedule()

        PersonhendelseOppgaveJob(
            personhendelseService = personhendelseService,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = initialDelay.next(),
            periode = if (isProd) {
                Duration.of(1, ChronoUnit.DAYS)
            } else {
                Duration.of(15, ChronoUnit.MINUTES)
            },
        ).schedule()

        KontrollsamtaleinnkallingJob(
            leaderPodLookup = clients.leaderPodLookup,
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
            sessionFactory = databaseRepos.sessionFactory,
        ).schedule()

        if (services.toggles.isEnabled(ToggleService.toggleForFeilutbetaling)) {
            TilbakekrevingIbmMqConsumer(
                queueName = applicationConfig.oppdrag.tilbakekreving.mq.mottak,
                globalJmsContext = jmsConfig.jmsContext,
                tilbakekrevingConsumer = tilbakekrevingConsumer,
            )

            TilbakekrevingJob(
                tilbakekrevingService = services.tilbakekrevingService,
                leaderPodLookup = clients.leaderPodLookup,
                initialDelay = initialDelay.next(),
                intervall = Duration.of(15, ChronoUnit.MINUTES),
            ).schedule()
        }
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
                utbetalingKvitteringConsumer = utbetalingKvitteringConsumer,
            ),
            initialDelay = initialDelay.next(),
            periode = Duration.ofSeconds(10),
        ).schedule()

        DistribuerDokumentJob(
            brevService = services.brev,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = initialDelay.next(),
            periode = Duration.ofSeconds(10),
        ).schedule()

        GrensesnittsavstemingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
            starttidspunkt = Date.from(Instant.now(clock).plusSeconds(initialDelay.next().toSeconds())),
            periode = Duration.ofMinutes(5),
        ).schedule()

        KonsistensavstemmingJob(
            avstemmingService = services.avstemming,
            leaderPodLookup = clients.leaderPodLookup,
            kjøreplan = emptySet(),
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(5),
            clock = clock,
        ).schedule()

        KlageinstanshendelseJob(
            klageinstanshendelseService = services.klageinstanshendelseService,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(5),
        ).schedule()

        PersonhendelseOppgaveJob(
            personhendelseService = personhendelseService,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(5),
        ).schedule()

        KontrollsamtaleinnkallingJob(
            leaderPodLookup = clients.leaderPodLookup,
            kontrollsamtaleService = services.kontrollsamtale,
            starttidspunkt = Date.from(Instant.now(clock).plusSeconds(initialDelay.next().toSeconds())),
            periode = Duration.ofMinutes(5),
            sessionFactory = databaseRepos.sessionFactory,
        ).schedule()

        LokalMottaKravgrunnlagJob(
            tilbakekrevingConsumer = tilbakekrevingConsumer,
            tilbakekrevingService = services.tilbakekrevingService,
            vedtakService = services.vedtakService,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
        ).schedule()

        TilbakekrevingJob(
            tilbakekrevingService = services.tilbakekrevingService,
            leaderPodLookup = clients.leaderPodLookup,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
        ).schedule()
    }
}
