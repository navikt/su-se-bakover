package no.nav.su.se.bakover.service

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.collections.shouldContain
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.service.behandling.BehandlingServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
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
                behandling = mock(),
                hendelseslogg = mock(),
                sak = mock(),
                person = mock(),
                vedtakssnapshot = mock(),
                saksbehandling = mock()
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
            (it.behandling as BehandlingServiceImpl).getObservers() shouldContain it.statistikk
            (it.saksbehandling as SaksbehandlingServiceImpl).getObservers() shouldContain it.statistikk
        }
    }
}
