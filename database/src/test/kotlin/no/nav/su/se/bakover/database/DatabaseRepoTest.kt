package no.nav.su.se.bakover.database

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class DatabaseRepoTest {
    private val repo = DatabaseRepo(EmbeddedDatabase.instance())

    @Test
    fun `lagre og hent behandling fra databasen`() {
        withMigratedDb {
            val behandling = enBehandling()
            val behandlingId = JSONObject(behandling.toJson()).getLong("id")
            val fromRepo = repo.hentBehandling(behandlingId)!!
            assertNotNull(fromRepo)
            assertEquals(behandling, fromRepo)
            assertFalse(JSONObject(fromRepo.toJson()).getJSONArray("vilkårsvurderinger").isEmpty)
        }
    }

    @Test
    fun `Sjekk at vi kan oppdatere vilkårsvurdering i en behandling`() {
        withMigratedDb {
            val behandling = enBehandling()
            val beforeUpdate = JSONObject(behandling.toJson()).getJSONArray("vilkårsvurderinger").getJSONObject(0)

            val fromFunction = JSONObject(
                behandling.oppdaterVilkårsvurderinger(
                    listOf(
                        Vilkårsvurdering(
                            id = 1,
                            vilkår = Vilkår.UFØRE,
                            begrunnelse = "begrunnelse",
                            status = Vilkårsvurdering.Status.IKKE_OK
                        )
                    )
                ).first().toJson()
            )

            val fromRepo = JSONObject(repo.hentVilkårsvurdering(beforeUpdate.getLong("id")).toJson())

            assertNotEquals(beforeUpdate.toString(), fromFunction.toString())
            assertNotEquals(beforeUpdate.toString(), fromRepo.toString())
            assertEquals(fromFunction.toString(), fromRepo.toString())
            assertEquals(Vilkårsvurdering.Status.IKKE_OK.name, fromFunction.getString("status"))
            assertEquals(Vilkårsvurdering.Status.IKKE_OK.name, fromRepo.getString("status"))
            assertEquals(fromFunction.getLong("id"), fromRepo.getLong("id"))
        }
    }

    private fun enSak() =
        repo.opprettSak(FnrGenerator.random()).also { it.nySøknad(SøknadInnholdTestdataBuilder.build()) }

    private fun enStønadsperiode(): Stønadsperiode = enSak().sisteStønadsperiode()

    private fun enBehandling() = enStønadsperiode().nyBehandling()
}
