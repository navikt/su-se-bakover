package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.next
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.dokument.infrastructure.Dokumentkomponenter
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.institusjonsopphold.application.service.EksternInstitusjonsoppholdKonsument
import no.nav.su.se.bakover.institusjonsopphold.application.service.OpprettOppgaverForInstitusjonsoppholdshendelser
import no.nav.su.se.bakover.institusjonsopphold.presentation.InstitusjonsoppholdConsumer
import no.nav.su.se.bakover.institusjonsopphold.presentation.InstitusjonsoppholdOppgaveJob
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs.KontrollsamtaleinnkallingJob
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs.StansYtelseVedManglendeOppmøteKontrollsamtaleJob
import no.nav.su.se.bakover.presentation.job.DokumentJobber
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.service.dokument.JournalførDokumentService
import no.nav.su.se.bakover.service.journalføring.JournalføringService
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import no.nav.su.se.bakover.web.services.SendPåminnelseNyStønadsperiodeJob
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.services.avstemming.GrensesnittsavstemingJob
import no.nav.su.se.bakover.web.services.avstemming.KonsistensavstemmingJob
import no.nav.su.se.bakover.web.services.dokument.DistribuerDokumentJob
import no.nav.su.se.bakover.web.services.dokument.JournalførDokumentJob
import no.nav.su.se.bakover.web.services.klage.klageinstans.KlageinstanshendelseConsumer
import no.nav.su.se.bakover.web.services.klage.klageinstans.KlageinstanshendelseJob
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseConsumer
import no.nav.su.se.bakover.web.services.personhendelser.PersonhendelseOppgaveJob
import no.nav.su.se.bakover.web.services.tilbakekreving.LokalMottaKravgrunnlagJob
import no.nav.su.se.bakover.web.services.tilbakekreving.SendTilbakekrevingsvedtakForRevurdering
import org.apache.kafka.clients.consumer.KafkaConsumer
import tilbakekreving.application.service.Tilbakekrevingskomponenter
import tilbakekreving.presentation.consumer.KravgrunnlagIbmMqConsumer
import tilbakekreving.presentation.job.Tilbakekrevingsjobber
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringIbmMqConsumer
import økonomi.infrastructure.kvittering.consumer.lokal.LokalKvitteringJob
import økonomi.infrastructure.kvittering.consumer.lokal.LokalKvitteringService
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
    consumers: Consumers,
    // TODO jah: Skal brukes når vi bytter over til ny jobb for å ferdigstille vedtak med utbetaling+kvittering
    @Suppress("UNUSED_PARAMETER") dbMetrics: DbMetrics,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    dokumentKomponenter: Dokumentkomponenter,
) {
    val personhendelseService = PersonhendelseService(
        sakRepo = databaseRepos.sak,
        personhendelseRepo = databaseRepos.personhendelseRepo,
        vedtakRepo = databaseRepos.vedtakRepo,
        oppgaveServiceImpl = services.oppgave,
        personService = services.person,
        clock = clock,
    )
    val runCheckFactory = RunCheckFactory(
        leaderPodLookup = clients.leaderPodLookup,
        applicationConfig = applicationConfig,
        clock = clock,
    )
    val distribuerDokumentService = DistribuerDokumentService(
        dokDistFordeling = clients.dokDistFordeling,
        dokumentRepo = databaseRepos.dokumentRepo,
    )
    val journalførDokumentService = JournalførDokumentService(
        dokArkiv = clients.dokArkiv,
        dokumentRepo = databaseRepos.dokumentRepo,
        sakService = services.sak,
        personService = services.person,
    )

    val journalførDokumentSkattService = JournalførSkattDokumentService(
        dokArkiv = clients.dokArkiv,
        sakService = services.sak,
        dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
    )

    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Nais) {
        // Prøver å time starten på jobbene slik at de ikke går i beina på hverandre.
        val initialDelay = object {
            var initialDelay: Duration = Duration.ofMinutes(5)
            fun next(): Duration {
                return initialDelay.also {
                    initialDelay = initialDelay.plus(Duration.ofSeconds(30))
                }
            }
        }
        val isProd = applicationConfig.naisCluster == ApplicationConfig.NaisCluster.Prod
        // TODO jah: startAsynkroneUtbetalingsprosesser skal erstatte den gamle UtbetalingKvitteringIbmMqConsumer
//        startAsynkroneUtbetalingsprosesser(
//            oppdragConfig = applicationConfig.oppdrag,
//            jmsConfig = jmsConfig,
//            sakService = services.sak,
//            sessionFactory = databaseRepos.sessionFactory,
//            clock = clock,
//            hendelsekonsumenterRepo = databaseRepos.hendelseKonsumenterRepo,
//            utbetalingService = services.utbetaling,
//            ferdigstillVedtakService = services.ferdigstillVedtak,
//            initalDelay = initialDelay::next,
//            runCheckFactory = runCheckFactory,
//            dbMetrics = dbMetrics,
//            hendelseRepo = databaseRepos.hendelseRepo as HendelsePostgresRepo,
//        )
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

        // holder inst på kun i dev inntil videre
        if (!isProd) {
            val institusjonsoppholdService = EksternInstitusjonsoppholdKonsument(
                institusjonsoppholdHendelseRepo = databaseRepos.institusjonsoppholdHendelseRepo,
                sakRepo = databaseRepos.sak,
                clock = clock,
            )
            InstitusjonsoppholdConsumer(
                config = applicationConfig.institusjonsoppholdKafkaConfig,
                institusjonsoppholdService = institusjonsoppholdService,
                clock = clock,
            )
            val hendelseskonsument = OpprettOppgaverForInstitusjonsoppholdshendelser(
                oppgaveService = services.oppgave,
                personService = services.person,
                institusjonsoppholdHendelseRepo = databaseRepos.institusjonsoppholdHendelseRepo,
                oppgaveHendelseRepo = databaseRepos.oppgaveHendelseRepo,
                hendelseRepo = databaseRepos.hendelseRepo,
                sakRepo = databaseRepos.sak,
                clock = clock,
                sessionFactory = databaseRepos.sessionFactory,
                hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
            )
            InstitusjonsoppholdOppgaveJob(
                hendelseskonsument = hendelseskonsument,
                periode = Duration.of(5, ChronoUnit.MINUTES),
                initialDelay = initialDelay.next(),
                runCheckFactory = runCheckFactory,
            ).schedule()
        }

        JournalførDokumentJob(
            initialDelay = initialDelay.next(),
            periode = Duration.of(5, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
            journalføringService = JournalføringService(
                journalførDokumentService = journalførDokumentService,
                journalførSkattDokumentService = journalførDokumentSkattService,
            ),
        ).schedule()

        DistribuerDokumentJob(
            initialDelay = initialDelay.next(),
            periode = Duration.of(5, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
            distribueringService = distribuerDokumentService,
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
            kontrollsamtaleService = services.kontrollsamtaleSetup.kontrollsamtaleService,
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

        DokumentJobber(
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
            journalførtDokumentHendelserKonsument = dokumentKomponenter.services.journalførtDokumentHendelserKonsument,
        ).schedule()

        KravgrunnlagIbmMqConsumer(
            queueName = applicationConfig.oppdrag.tilbakekreving.mq.mottak,
            globalJmsContext = jmsConfig.jmsContext,
            service = tilbakekrevingskomponenter.services.råttKravgrunnlagService,
        )

        Tilbakekrevingsjobber(
            knyttKravgrunnlagTilSakOgUtbetalingKonsument = tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument,
            opprettOppgaveKonsument = tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument,
            genererDokumenterForForhåndsvarselKonsument = tilbakekrevingskomponenter.services.genererDokumentForForhåndsvarselTilbakekrevingKonsument,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
        ).schedule()

        SendTilbakekrevingsvedtakForRevurdering(
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
            service = services.kontrollsamtaleSetup.utløptFristForKontrollsamtaleService,
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

        JournalførDokumentJob(
            initialDelay = initialDelay.next(),
            periode = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
            journalføringService = JournalføringService(
                journalførDokumentService = journalførDokumentService,
                journalførSkattDokumentService = journalførDokumentSkattService,
            ),
        ).schedule()

        DistribuerDokumentJob(
            initialDelay = initialDelay.next(),
            periode = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
            distribueringService = distribuerDokumentService,
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
            kontrollsamtaleService = services.kontrollsamtaleSetup.kontrollsamtaleService,
            starttidspunkt = Date.from(Instant.now(clock).plusSeconds(initialDelay.next().toSeconds())),
            periode = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
        ).schedule()

        LokalMottaKravgrunnlagJob(
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
            sessionFactory = databaseRepos.sessionFactory,
            service = tilbakekrevingskomponenter.services.råttKravgrunnlagService,
            clock = clock,
        ).schedule()

        Tilbakekrevingsjobber(
            knyttKravgrunnlagTilSakOgUtbetalingKonsument = tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument,
            opprettOppgaveKonsument = tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument,
            genererDokumenterForForhåndsvarselKonsument = tilbakekrevingskomponenter.services.genererDokumentForForhåndsvarselTilbakekrevingKonsument,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
        ).schedule()

        DokumentJobber(
            initialDelay = initialDelay.next(),
            intervall = Duration.ofSeconds(10),
            runCheckFactory = runCheckFactory,
            journalførtDokumentHendelserKonsument = dokumentKomponenter.services.journalførtDokumentHendelserKonsument,
        ).schedule()

        SendTilbakekrevingsvedtakForRevurdering(
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
            service = services.kontrollsamtaleSetup.utløptFristForKontrollsamtaleService,
            clock = clock,
            runCheckFactory = runCheckFactory,
        ).schedule()
    }
}
