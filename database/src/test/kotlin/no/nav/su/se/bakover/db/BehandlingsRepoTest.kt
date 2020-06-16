package no.nav.su.se.bakover.db

import no.nav.su.se.bakover.BehandlingFactory
import no.nav.su.se.bakover.DatabaseSøknadRepo
import no.nav.su.se.bakover.Fødselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BehandlingsRepoTest {

    @Test
    fun `Sjekk at vi kan lagre en behandling i basen`() {
       withMigratedDb {
           val repo = DatabaseSøknadRepo(EmbeddedDatabase.database)
           val behandlingFactory = BehandlingFactory(repo)

           val stønadsperiodeId = repo.lagreStønadsperiode(repo.nySak(Fødselsnummer("12345678901")), repo.lagreSøknad("{}"))
           var lagretBehandlingsId = behandlingFactory.opprett(stønadsperiodeId).id()

           val lestBehandlingsId = repo.hentBehandling(lagretBehandlingsId)
           assertEquals(lagretBehandlingsId, lestBehandlingsId)
       }
    }
}