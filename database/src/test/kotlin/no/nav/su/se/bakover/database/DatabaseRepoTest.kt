package no.nav.su.se.bakover.database

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

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
                            id = beforeUpdate.id,
                            vilkår = Vilkår.UFØRHET,
                            begrunnelse = "begrunnelse",
                            status = Vilkårsvurdering.Status.IKKE_OK
                        )
                    )
                ).first().toDto()

            val fromRepo = repo.hentVilkårsvurdering(beforeUpdate.id)!!.toDto()

            assertNotEquals(beforeUpdate, fromFunction)
            assertNotEquals(beforeUpdate, fromRepo)
            assertEquals(Vilkårsvurdering.Status.IKKE_OK.name, fromFunction.status.name)
            assertEquals(Vilkårsvurdering.Status.IKKE_OK.name, fromRepo.status.name)
            assertEquals("begrunnelse", fromFunction.begrunnelse)
            assertEquals("begrunnelse", fromRepo.begrunnelse)
            assertEquals(fromFunction.id, fromRepo.id)
            assertEquals(fromFunction.vilkår, fromRepo.vilkår)
        }
    }

    @Test
    fun `unknown entities`() {
        withMigratedDb {
            assertNull(repo.hentSak(FnrGenerator.random()))
            assertNull(repo.hentSak(UUID.randomUUID()))
            assertNull(repo.hentVilkårsvurdering(UUID.randomUUID()))
            assertTrue(repo.hentVilkårsvurderinger(UUID.randomUUID()).isEmpty())
            assertNull(repo.hentBehandling(UUID.randomUUID()))
            assertNull(repo.hentSøknad(UUID.randomUUID()))
        }
    }

    private fun enSak() =
        repo.opprettSak(FnrGenerator.random()).also { it.nySøknad(SøknadInnholdTestdataBuilder.build()) }

    private fun enBehandling(): Behandling {
        val sak = enSak()
        return sak.opprettSøknadsbehandling(sak.toDto().søknader.first().id)
    }
}
