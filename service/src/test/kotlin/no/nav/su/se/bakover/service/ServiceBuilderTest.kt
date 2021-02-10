package no.nav.su.se.bakover.service

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.collections.shouldContain
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import org.junit.jupiter.api.Test
import java.time.Clock

internal class ServiceBuilderTest {
    @Test
    fun `StatistikkService observerer SakService`() {
        ProdServiceBuilder.build(
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
                kafkaPublisher = mock()
            ),
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = Clock.systemUTC(),
            unleash = mock()
        ).let {
            (it.sak as SakServiceImpl).observers shouldContain it.statistikk
            (it.søknadsbehandling as SøknadsbehandlingServiceImpl).getObservers() shouldContain it.statistikk
            (it.ferdigstillIverksettingService as FerdigstillIverksettingServiceImpl).getObservers() shouldContain it.statistikk
        }
    }
}
