package no.nav.su.se.bakover.db

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.DatabaseSøknadRepo
import no.nav.su.se.bakover.Fødselsnummer
import no.nav.su.se.bakover.Stønadsperiode
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class BehandlingsRepoTest {

    private val repo = DatabaseSøknadRepo(EmbeddedDatabase.database)

    @Test
    fun `Sjekk at vi kan lagre en behandling i basen`() {
        withMigratedDb {
            val stønadsperiode = enStønadsperiode().also {
                it.nyBehandling()
            }
            val behandlingId = JSONObject(stønadsperiode.toJson()).getJSONArray("behandlinger").getJSONObject(0).getLong("id")
            assertNotNull(repo.hentBehandling(behandlingId))
        }
    }

    private fun enStønadsperiode(): Stønadsperiode = repo.opprettSak(Fødselsnummer("12345678910")).also {
        it.nySøknad(SøknadInnholdTestdataBuilder.build())
    }.sisteStønadsperiode()
}