package no.nav.su.se.bakover.service

import io.kotest.matchers.collections.shouldContain
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.satsFactoryTest
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
                sessionFactory = mock(),
                klageRepo = mock(),
                klageinstanshendelseRepo = mock(),
                kontrollsamtaleRepo = mock(),
                avkortingsvarselRepo = mock(),
                reguleringRepo = defaultMock(),
                tilbakekrevingRepo = mock(),
                jobContextRepo = mock(),
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
                digitalKontaktinformasjon = mock(),
                leaderPodLookup = mock(),
                kafkaPublisher = mock(),
                klageClient = mock(),
                journalpostClient = mock(),
                tilbakekrevingClient = mock(),
                skatteOppslag = mock(),
                maskinportenClient = mock()
            ),
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = Clock.systemUTC(),
            unleash = mock(),
            satsFactory = satsFactoryTest,
        ).let {
            (it.sak as SakServiceImpl).observers shouldContain it.statistikk
            (it.søknadsbehandling as SøknadsbehandlingServiceImpl).getObservers() shouldContain it.statistikk
            (it.revurdering as RevurderingServiceImpl).getObservers() shouldContain it.statistikk
            // TODO add statistikk
//            (it.ferdigstillVedtak as FerdigstillVedtakService).getObservers() shouldContain it.statistikk
        }
    }
}
