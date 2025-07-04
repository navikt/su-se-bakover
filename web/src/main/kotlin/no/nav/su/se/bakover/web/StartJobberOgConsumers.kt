package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.domain.extensions.next
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.JobberOgConsumers
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.NaisCluster
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
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
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import no.nav.su.se.bakover.service.søknad.job.FiksSøknaderUtenOppgave
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
import org.apache.kafka.clients.consumer.KafkaConsumer
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import tilbakekreving.presentation.consumer.KravgrunnlagIbmMqConsumer
import tilbakekreving.presentation.job.Tilbakekrevingsjobber
import økonomi.infrastructure.kvittering.UtbetalingskvitteringKomponenter
import økonomi.infrastructure.kvittering.consumer.kvitteringXmlTilSaksnummerOgUtbetalingId
import økonomi.infrastructure.kvittering.startAsynkroneUtbetalingsprosesser
import økonomi.infrastructure.kvittering.xmlMapperForUtbetalingskvittering
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
    dbMetrics: DbMetrics,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    dokumentKomponenter: Dokumentkomponenter,
    distribuerDokumentService: DistribuerDokumentService,
): JobberOgConsumers {
    val runCheckFactory = RunCheckFactory(
        leaderPodLookup = clients.leaderPodLookup,
        applicationConfig = applicationConfig,
        clock = clock,
    )

    val journalførDokumentService = JournalførDokumentService(
        journalførBrevClient = clients.journalførClients.brev,
        dokumentRepo = databaseRepos.dokumentRepo,
        sakService = services.sak,
    )

    val journalførDokumentSkattService = JournalførSkattDokumentService(
        journalførSkattedokumentPåSakClient = clients.journalførClients.skattedokumentPåSak,
        journalførSkattedokumentUtenforSakClient = clients.journalførClients.skattedokumentUtenforSak,
        sakService = services.sak,
        dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
    )

    val initialDelay = InitialDelay(applicationConfig.runtimeEnvironment)

    val utbetalingskvitteringKomponenter = UtbetalingskvitteringKomponenter.create(
        sakService = services.sak,
        sessionFactory = databaseRepos.sessionFactory,
        clock = clock,
        hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
        hendelseRepo = databaseRepos.hendelseRepo,
        dbMetrics = dbMetrics,
        utbetalingService = services.utbetaling,
        ferdigstillVedtakService = services.ferdigstillVedtak,
        xmlMapperForUtbetalingskvittering = kvitteringXmlTilSaksnummerOgUtbetalingId(
            xmlMapperForUtbetalingskvittering,
        ),
    )
    val oppdragJobberOgConsumers = startAsynkroneUtbetalingsprosesser(
        utbetalingskvitteringKomponenter = utbetalingskvitteringKomponenter,
        oppdragConfig = applicationConfig.oppdrag,
        jmsConfig = jmsConfig,
        initalDelay = initialDelay::next,
        runCheckFactory = runCheckFactory,
        runtimeEnvironment = applicationConfig.runtimeEnvironment,
        utbetalingRepo = databaseRepos.utbetaling,
    )

    return when (applicationConfig.runtimeEnvironment) {
        ApplicationConfig.RuntimeEnvironment.Nais -> naisJobberOgConsumers(
            services = services,
            databaseRepos = databaseRepos,
            applicationConfig = applicationConfig,
            clock = clock,
            runCheckFactory = runCheckFactory,
            dokumentKomponenter = dokumentKomponenter,
            distribuerDokumentService = distribuerDokumentService,
            initialDelay = initialDelay,
            journalførDokumentService = journalførDokumentService,
            journalførDokumentSkattService = journalførDokumentSkattService,
            jmsConfig = jmsConfig,
            tilbakekrevingskomponenter = tilbakekrevingskomponenter,
        )

        ApplicationConfig.RuntimeEnvironment.Local ->
            localJobberOgConsumers(
                initialDelay,
                runCheckFactory,
                journalførDokumentService,
                journalførDokumentSkattService,
                distribuerDokumentService,
                services,
                clock,
                databaseRepos,
                tilbakekrevingskomponenter,
                dokumentKomponenter,
            )

        ApplicationConfig.RuntimeEnvironment.Test -> JobberOgConsumers(
            jobs = emptyList(),
            consumers = emptyList(),
        )
    }.plus(oppdragJobberOgConsumers)
}

private fun localJobberOgConsumers(
    initialDelay: InitialDelay,
    runCheckFactory: RunCheckFactory,
    journalførDokumentService: JournalførDokumentService,
    journalførDokumentSkattService: JournalførSkattDokumentService,
    distribuerDokumentService: DistribuerDokumentService,
    services: Services,
    clock: Clock,
    databaseRepos: DatabaseRepos,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    dokumentKomponenter: Dokumentkomponenter,
): JobberOgConsumers {
    val jobber = listOf(
        JournalførDokumentJob.startJob(
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(1),
            runCheckFactory = runCheckFactory,
            journalføringService = JournalføringService(
                journalførDokumentService = journalførDokumentService,
                journalførSkattDokumentService = journalførDokumentSkattService,
            ),
        ),

        DistribuerDokumentJob.startJob(
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(1),
            runCheckFactory = runCheckFactory,
            distribueringService = distribuerDokumentService,
        ),

        GrensesnittsavstemingJob.startJob(
            avstemmingService = services.avstemming,
            starttidspunkt = Date.from(Instant.now(clock).plusSeconds(initialDelay.next().toSeconds())),
            periode = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
        ),

        KonsistensavstemmingJob.startJob(
            avstemmingService = services.avstemming,
            kjøreplan = emptySet(),
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(5),
            clock = clock,
            runCheckFactory = runCheckFactory,
        ),

        KlageinstanshendelseJob.startJob(
            klageinstanshendelseService = services.klageinstanshendelseService,
            initialDelay = initialDelay.next(),
            periode = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
        ),

        PersonhendelseOppgaveJob.startJob(
            personhendelseService = services.personhendelseService,
            periode = Duration.ofMinutes(5),
            initialDelay = initialDelay.next(),
            runCheckFactory = runCheckFactory,
        ),

        KontrollsamtaleinnkallingJob.startJob(
            kontrollsamtaleService = services.kontrollsamtaleSetup.kontrollsamtaleService,
            starttidspunkt = Date.from(Instant.now(clock).plusSeconds(initialDelay.next().toSeconds())),
            periode = Duration.ofMinutes(5),
            runCheckFactory = runCheckFactory,
        ),

        LokalMottaKravgrunnlagJob.startJob(
            initialDelay = initialDelay.next(),
            intervall = Duration.ofMinutes(1),
            sessionFactory = databaseRepos.sessionFactory,
            service = tilbakekrevingskomponenter.services.råttKravgrunnlagService,
            clock = clock,
        ),

        Tilbakekrevingsjobber.startJob(
            knyttKravgrunnlagTilSakOgUtbetalingKonsument = tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument,
            opprettOppgaveKonsument = tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument,
            genererDokumenterForForhåndsvarselKonsument = tilbakekrevingskomponenter.services.genererDokumentForForhåndsvarselTilbakekrevingKonsument,
            lukkOppgaveKonsument = tilbakekrevingskomponenter.services.lukkOppgaveForTilbakekrevingshendelserKonsument,
            oppdaterOppgaveKonsument = tilbakekrevingskomponenter.services.oppdaterOppgaveForTilbakekrevingshendelserKonsument,
            genererVedtaksbrevTilbakekrevingKonsument = tilbakekrevingskomponenter.services.vedtaksbrevTilbakekrevingKonsument,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofMinutes(1),
            runCheckFactory = runCheckFactory,
        ),

        DokumentJobber.startJob(
            initialDelay = initialDelay.next(),
            intervall = Duration.ofMinutes(1),
            runCheckFactory = runCheckFactory,
            journalførtDokumentHendelserKonsument = dokumentKomponenter.services.journalførtDokumentHendelserKonsument,
            distribuerDokumentHendelserKonsument = dokumentKomponenter.services.distribuerDokumentHendelserKonsument,
        ),

        SendPåminnelseNyStønadsperiodeJob.startJob(
            intervall = Duration.ofMinutes(1),
            initialDelay = initialDelay.next(),
            sendPåminnelseService = services.sendPåminnelserOmNyStønadsperiodeService,
            runCheckFactory = runCheckFactory,
        ),

        StansYtelseVedManglendeOppmøteKontrollsamtaleJob.startJob(
            intervall = Duration.ofMinutes(1),
            initialDelay = initialDelay.next(),
            service = services.kontrollsamtaleSetup.utløptFristForKontrollsamtaleService,
            runCheckFactory = runCheckFactory,
        ),
    )
    return JobberOgConsumers(
        jobs = jobber,
        // Vi starter ikke consumers lokalt. Disse må emuleres manuelt eller erstattes av jobber. Som f.eks. LokalMottaKravgrunnlagJob
        consumers = emptyList(),
    )
}

private fun naisJobberOgConsumers(
    services: Services,
    databaseRepos: DatabaseRepos,
    applicationConfig: ApplicationConfig,
    clock: Clock,
    runCheckFactory: RunCheckFactory,
    dokumentKomponenter: Dokumentkomponenter,
    distribuerDokumentService: DistribuerDokumentService,
    initialDelay: InitialDelay,
    journalførDokumentService: JournalførDokumentService,
    journalførDokumentSkattService: JournalførSkattDokumentService,
    jmsConfig: JmsConfig,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
): JobberOgConsumers {
    val isProd = applicationConfig.naisCluster == NaisCluster.Prod

    val jobber = listOfNotNull(
        // holder inst på kun i preprod inntil videre
        if (!isProd) {
            val hendelseskonsument = OpprettOppgaverForInstitusjonsoppholdshendelser(
                oppgaveService = services.oppgave,
                institusjonsoppholdHendelseRepo = databaseRepos.institusjonsoppholdHendelseRepo,
                oppgaveHendelseRepo = databaseRepos.oppgaveHendelseRepo,
                hendelseRepo = databaseRepos.hendelseRepo,
                sakRepo = databaseRepos.sak,
                clock = clock,
                sessionFactory = databaseRepos.sessionFactory,
                hendelsekonsumenterRepo = databaseRepos.hendelsekonsumenterRepo,
            )
            InstitusjonsoppholdOppgaveJob.startJob(
                hendelseskonsument = hendelseskonsument,
                periode = Duration.of(5, ChronoUnit.MINUTES),
                initialDelay = initialDelay.next(),
                runCheckFactory = runCheckFactory,
            )
        } else {
            null
        },

        JournalførDokumentJob.startJob(
            initialDelay = initialDelay.next(),
            periode = Duration.of(5, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
            journalføringService = JournalføringService(
                journalførDokumentService = journalførDokumentService,
                journalførSkattDokumentService = journalførDokumentSkattService,
            ),
        ),

        DistribuerDokumentJob.startJob(
            initialDelay = initialDelay.next(),
            periode = Duration.of(5, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
            distribueringService = distribuerDokumentService,
        ),

        GrensesnittsavstemingJob.startJob(
            avstemmingService = services.avstemming,
            starttidspunkt = ZonedDateTime.now(zoneIdOslo).next(LocalTime.of(1, 0, 0)),
            periode = Duration.of(1, ChronoUnit.DAYS),
            runCheckFactory = runCheckFactory,
        ),

        KonsistensavstemmingJob.startJob(
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
                    // ------- 2024
                    4.januar(2024),
                    30.januar(2024),
                    28.februar(2024),
                    29.mars(2024),
                    25.april(2024),
                    24.mai(2024),
                    28.juni(2024),
                    30.juli(2024),
                    30.august(2024),
                    27.september(2024),
                    30.oktober(2024),
                    25.november(2024),
                    // ------- 2025
                    13.januar(2025),
                    30.januar(2025),
                    27.februar(2025),
                    27.mars(2025),
                    29.april(2025),
                    23.mai(2025),
                    27.juni(2025),
                    30.juli(2025),
                    1.august(2025),
                    29.september(2025),
                    30.oktober(2025),
                    21.november(2025),
                )
            } else {
                emptySet()
            },
            initialDelay = initialDelay.next(),
            // Kjører hver fjerde time for å være rimelig sikker på at jobben faktisk blir kjørt
            periode = Duration.of(4, ChronoUnit.HOURS),
            clock = clock,
            runCheckFactory = runCheckFactory,
        ),

        KlageinstanshendelseJob.startJob(
            klageinstanshendelseService = services.klageinstanshendelseService,
            initialDelay = initialDelay.next(),
            periode = Duration.of(15, ChronoUnit.MINUTES),
            runCheckFactory = runCheckFactory,
        ),

        PersonhendelseOppgaveJob.startJob(
            personhendelseService = services.personhendelseService,
            initialDelay = initialDelay.next(),
            periode = if (isProd) {
                Duration.of(1, ChronoUnit.DAYS)
            } else {
                Duration.of(15, ChronoUnit.MINUTES)
            },
            runCheckFactory = runCheckFactory,
        ),

        KontrollsamtaleinnkallingJob.startJob(
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
        ),

        DokumentJobber.startJob(
            initialDelay = initialDelay.next(),
            intervall = Duration.ofMinutes(4),
            runCheckFactory = runCheckFactory,
            journalførtDokumentHendelserKonsument = dokumentKomponenter.services.journalførtDokumentHendelserKonsument,
            distribuerDokumentHendelserKonsument = dokumentKomponenter.services.distribuerDokumentHendelserKonsument,
        ),
        Tilbakekrevingsjobber.startJob(
            knyttKravgrunnlagTilSakOgUtbetalingKonsument = tilbakekrevingskomponenter.services.knyttKravgrunnlagTilSakOgUtbetalingKonsument,
            opprettOppgaveKonsument = tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument,
            genererDokumenterForForhåndsvarselKonsument = tilbakekrevingskomponenter.services.genererDokumentForForhåndsvarselTilbakekrevingKonsument,
            lukkOppgaveKonsument = tilbakekrevingskomponenter.services.lukkOppgaveForTilbakekrevingshendelserKonsument,
            oppdaterOppgaveKonsument = tilbakekrevingskomponenter.services.oppdaterOppgaveForTilbakekrevingshendelserKonsument,
            genererVedtaksbrevTilbakekrevingKonsument = tilbakekrevingskomponenter.services.vedtaksbrevTilbakekrevingKonsument,
            initialDelay = initialDelay.next(),
            intervall = Duration.ofMinutes(4),
            runCheckFactory = runCheckFactory,
        ),

        SendPåminnelseNyStønadsperiodeJob.startJob(
            intervall = Duration.of(4, ChronoUnit.HOURS),
            initialDelay = initialDelay.next(),
            sendPåminnelseService = services.sendPåminnelserOmNyStønadsperiodeService,
            runCheckFactory = runCheckFactory,
        ),

        StansYtelseVedManglendeOppmøteKontrollsamtaleJob.startJob(
            intervall = Duration.of(2, ChronoUnit.HOURS),
            initialDelay = initialDelay.next(),
            service = services.kontrollsamtaleSetup.utløptFristForKontrollsamtaleService,
            runCheckFactory = runCheckFactory,
        ),

        FiksSøknaderUtenOppgave.startJob(
            intervall = Duration.of(1, ChronoUnit.HOURS),
            initialDelay = initialDelay.next(),
            søknadService = services.søknad,
            runCheckFactory = runCheckFactory,
        ),

    )

    val consumers = listOfNotNull(
        // holder inst på kun i preprod inntil videre
        if (!isProd) {
            val institusjonsoppholdService = EksternInstitusjonsoppholdKonsument(
                institusjonsoppholdHendelseRepo = databaseRepos.institusjonsoppholdHendelseRepo,
                sakRepo = databaseRepos.sak,
                clock = clock,
            )
            InstitusjonsoppholdConsumer(
                config = applicationConfig.institusjonsoppholdKafkaConfig,
                institusjonsoppholdService = institusjonsoppholdService,
            )
        } else {
            null
        },
        KravgrunnlagIbmMqConsumer(
            queueName = applicationConfig.oppdrag.tilbakekreving.mq.mottak,
            globalJmsContext = jmsConfig.jmsContext,
            service = tilbakekrevingskomponenter.services.råttKravgrunnlagService,
        ),
        PersonhendelseConsumer(
            consumer = KafkaConsumer(applicationConfig.kafkaConfig.consumerCfg.kafkaConfig),
            personhendelseService = services.personhendelseService,
            clock = clock,
        ),
        KlageinstanshendelseConsumer(
            consumer = KafkaConsumer(applicationConfig.kabalKafkaConfig.kafkaConfig),
            klageinstanshendelseService = services.klageinstanshendelseService,
            clock = clock,
        ),
    )
    return JobberOgConsumers(
        jobs = jobber,
        consumers = consumers,
    )
}
