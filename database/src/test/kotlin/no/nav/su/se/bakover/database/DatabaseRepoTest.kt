package no.nav.su.se.bakover.database

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DatabaseRepoTest {
    private val repo = DatabaseRepo(EmbeddedDatabase.instance())

    @Test
    fun `lagre og hent behandling fra databasen`() {
        withMigratedDb {
            val behandling = enBehandling()
            val behandlingDto = behandling.toDto()
            val fromRepo = repo.hentBehandling(behandlingDto.id)!!
            assertNotNull(fromRepo)
            assertEquals(behandling, fromRepo)
            assertFalse(fromRepo.toDto().vilkårsvurderinger.isEmpty())
        }
    }

    @Test
    fun `Sjekk at vi kan oppdatere vilkårsvurdering i en behandling`() {
        withMigratedDb {
            val behandling = enBehandling()
            val beforeUpdate = behandling.toDto().vilkårsvurderinger.first()

            val fromFunction = behandling.oppdaterVilkårsvurderinger(
                    listOf(
                        Vilkårsvurdering(
                            id = 1,
                            vilkår = Vilkår.UFØRHET,
                            begrunnelse = "begrunnelse",
                            status = Vilkårsvurdering.Status.IKKE_OK
                        )
                    )
                ).first().toDto()

            val fromRepo = repo.hentVilkårsvurdering(beforeUpdate.id)!!.toDto()

            assertNotEquals(beforeUpdate, fromFunction)
            assertNotEquals(beforeUpdate, fromRepo)
            assertEquals(fromFunction, fromRepo)
            assertEquals(Vilkårsvurdering.Status.IKKE_OK.name, fromFunction.status.name)
            assertEquals(Vilkårsvurdering.Status.IKKE_OK.name, fromRepo.status.name)
            assertEquals(fromFunction.id, fromRepo.id)
        }
    }

    @Test
    fun `unknown entities`() {
        withMigratedDb {
            assertNull(repo.hentSak(FnrGenerator.random()))
            assertNull(repo.hentSak(Long.MAX_VALUE))
            assertNull(repo.hentStønadsperiode(Long.MAX_VALUE))
            assertTrue(repo.hentStønadsperioder(Long.MAX_VALUE).isEmpty())
            assertNull(repo.hentVilkårsvurdering(Long.MAX_VALUE))
            assertTrue(repo.hentVilkårsvurderinger(Long.MAX_VALUE).isEmpty())
            assertNull(repo.hentBehandling(Long.MAX_VALUE))
            assertNull(repo.hentSøknad(Long.MAX_VALUE))
        }
    }

    private fun enSak() =
        repo.opprettSak(FnrGenerator.random()).also { it.nySøknad(SøknadInnholdTestdataBuilder.build()) }

    private fun enStønadsperiode(): Stønadsperiode = enSak().sisteStønadsperiode()

    private fun enBehandling() = enStønadsperiode().nyBehandling()
}
