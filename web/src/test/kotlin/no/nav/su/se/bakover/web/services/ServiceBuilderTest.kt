package no.nav.su.se.bakover.web.services

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.klage.KlageServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Clock

internal class ServiceBuilderTest {

    @Test
    fun `StatistikkService observerer SakService`() {
        ServiceBuilder.build(
            databaseRepos = DatabaseRepos(
                avstemming = mock(),
                utbetaling = mock(),
                søknad = mock(),
                sak = mock(),
                person = mock(),
                søknadsbehandling = mock(),
                revurderingRepo = mock(),
                vedtakRepo = mock(),
                personhendelseRepo = mock(),
                dokumentRepo = mock(),
                nøkkeltallRepo = mock(),
                sessionFactory = mock<PostgresSessionFactory>(),
                klageRepo = mock(),
                klageinstanshendelseRepo = mock(),
                avkortingsvarselRepo = mock(),
                reguleringRepo = defaultMock(),
                tilbakekrevingRepo = mock(),
                hendelseRepo = mock(),
                utenlandsoppholdRepo = mock(),
                sendPåminnelseNyStønadsperiodeJobRepo = mock(),
            ),
            clients = Clients(
                oauth = mock(),
                personOppslag = mock(),
                tokenOppslag = mock(),
                pdfGenerator = mock(),
                dokArkiv = mock(),
                oppgaveClient = mock(),
                kodeverk = mock(),
                simuleringClient = mock(),
                utbetalingPublisher = mock(),
                dokDistFordeling = mock(),
                avstemmingPublisher = mock(),
                identClient = mock(),
                kontaktOgReservasjonsregister = mock(),
                leaderPodLookup = mock(),
                kafkaPublisher = mock(),
                klageClient = mock(),
                journalpostClient = mock(),
                tilbakekrevingClient = mock(),
                skatteOppslag = mock(),
                maskinportenClient = mock(),
            ),
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = Clock.systemUTC(),
            unleash = mock(),
            satsFactory = satsFactoryTestPåDato(),
            applicationConfig = applicationConfig(),
            dbMetrics = dbMetricsStub,
        ).let {
            listOf(
                (it.sak as SakServiceImpl).getObservers().singleOrNull(),
                (it.søknad as SøknadServiceImpl).getObservers().singleOrNull(),
                (it.revurdering as RevurderingServiceImpl).getObservers().singleOrNull(),
                (it.reguleringService as ReguleringServiceImpl).getObservers().singleOrNull(),
                (it.søknadsbehandling.søknadsbehandlingService as SøknadsbehandlingServiceImpl).getObservers()
                    .singleOrNull(),
                (it.klageService as KlageServiceImpl).getObservers().singleOrNull(),
                (it.lukkSøknad as LukkSøknadServiceImpl).getObservers().singleOrNull(),
            ).forEach {
                withClue("$it should implement StatistikkEventObserver, but didn't") {
                    (it is StatistikkEventObserver).shouldBe(true)
                }
            }
        }
    }
}
