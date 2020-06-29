package no.nav.su.se.bakover.database

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class DatabaseRepoTest {
    private val repo = DatabaseRepo(EmbeddedDatabase.instance())

    @Test
    fun `Sjekk at vi kan lagre en behandling i basen`() {
        withMigratedDb {
            val behandlingId = JSONObject(enBehandling().toJson()).getLong("id")
            assertNotNull(repo.hentBehandling(behandlingId))
        }
    }

    @Test
    fun `Sjekk at vi kan oppdatere vilkår i en behandling`() {
        withMigratedDb {
            val behandling = enBehandling()
            val list = listOf(Vilkårsvurdering(1, Vilkår.UFØRE, "begrunnelse", Vilkårsvurdering.Status.IKKE_OK))
            behandling.oppdaterVilkårsvurderinger(list)

            assertEquals(repo.hentVilkårsvurdering(1), list[0])
        }
    }

    private fun enSak() = repo.opprettSak(FnrGenerator.random()).also { it.nySøknad(SøknadInnholdTestdataBuilder.build()) }

    private fun enStønadsperiode(): Stønadsperiode = enSak().sisteStønadsperiode()

    private fun enBehandling() = enStønadsperiode().nyBehandling()
}