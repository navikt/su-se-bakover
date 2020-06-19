package no.nav.su.se.bakover.database

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Stønadsperiode
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class BehandlingsRepoTest {

    private val repo = DatabaseRepo(EmbeddedDatabase.database)

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

    private fun enStønadsperiode(): Stønadsperiode = repo.opprettSak(Fnr("12345678910")).also {
        it.nySøknad(SøknadInnholdTestdataBuilder.build())
    }.sisteStønadsperiode()
}