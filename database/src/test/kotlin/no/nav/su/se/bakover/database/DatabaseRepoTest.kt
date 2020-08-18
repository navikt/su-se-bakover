package no.nav.su.se.bakover.database

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Vilkår.UFØRHET
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class DatabaseRepoTest {

    private val repo = DatabaseRepo(EmbeddedDatabase.instance())

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

    @Test
    fun `opprett og hent oppdrag og oppdragslinjer`() {
        // TODO: La denne testklassen teste kun repo impl
        withMigratedDb {
            val sak = enSak()
            val behandling = sak.opprettSøknadsbehandling(sak.toDto().søknader.first().id)
            behandling.oppdaterVilkårsvurderinger(
                listOf(
                    Vilkårsvurdering(
                        id = behandling.toDto().vilkårsvurderinger.first { it.vilkår == UFØRHET }.id,
                        vilkår = UFØRHET,
                        begrunnelse = "begrunnelse",
                        status = Vilkårsvurdering.Status.IKKE_OK
                    )
                )
            )
            behandling.opprettBeregning(
                fom = 1.januar(2020),
                tom = 31.desember(2020)
            )
            sak.fullførBehandling(
                behandling.toDto().id,
                object : Sak.OppdragClient {
                    override fun simuler(oppdrag: Oppdrag) = Simulering(
                        gjelderId = "",
                        gjelderNavn = "",
                        datoBeregnet = LocalDate.now(),
                        totalBelop = 1,
                        periodeList = emptyList()
                    ).right()
                }
            )
            val expectedSak = repo.hentSak(sak.toDto().fnr)!!
            sak.toDto() shouldBe expectedSak.toDto()
        }
    }

    private fun enSak() =
        repo.opprettSak(FnrGenerator.random()).also { it.nySøknad(SøknadInnholdTestdataBuilder.build()) }
}
