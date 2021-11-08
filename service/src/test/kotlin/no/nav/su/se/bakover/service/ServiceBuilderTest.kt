package no.nav.su.se.bakover.service

import io.kotest.matchers.collections.shouldContain
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
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
                hendelseslogg = mock(),
                sak = mock(),
                person = mock(),
                vedtakssnapshot = mock(),
                søknadsbehandling = mock(),
                revurderingRepo = mock(),
                vedtakRepo = mock(),
                grunnlagRepo = mock(),
                uføreVilkårsvurderingRepo = mock(),
                formueVilkårsvurderingRepo = mock(),
                personhendelseRepo = mock(),
                dokumentRepo = mock(),
                sessionFactory = mock(),
                nøkkeltallRepo = mock(),
                tilbakekrevingRepo = mock(),
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
                microsoftGraphApiClient = mock(),
                digitalKontaktinformasjon = mock(),
                leaderPodLookup = mock(),
                kafkaPublisher = mock(),
            ),
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = Clock.systemUTC(),
            unleash = mock(),
        ).let {
            (it.sak as SakServiceImpl).observers shouldContain it.statistikk
            (it.søknadsbehandling as SøknadsbehandlingServiceImpl).getObservers() shouldContain it.statistikk
            (it.revurdering as RevurderingServiceImpl).getObservers() shouldContain it.statistikk
            // TODO add statistikk
//            (it.ferdigstillVedtak as FerdigstillVedtakService).getObservers() shouldContain it.statistikk
        }
    }
}
