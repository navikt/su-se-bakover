package no.nav.su.se.bakover.database.behandling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import org.junit.jupiter.api.Test

internal class BehandlingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = BehandlingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent behandling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)

            val hentet = repo.hentBehandling(behandling.id)

            behandling shouldBe hentet
        }
    }

    @Test
    fun `oppdater behandlingsinformasjon`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)

            val oppdatert = repo.oppdaterBehandlingsinformasjon(
                behandling.id,
                Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                        uføregrad = 40,
                        forventetInntekt = 200
                    )
                )
            )

            val hentet = repo.hentBehandling(behandling.id)!!

            oppdatert.behandlingsinformasjon() shouldBe hentet.behandlingsinformasjon()
            behandling.behandlingsinformasjon() shouldNotBe hentet.behandlingsinformasjon()
        }
    }

    @Test
    fun `saksbehandle behandling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)

            val saksbehandler = repo.settSaksbehandler(behandling.id, Saksbehandler("Per"))
            val hentet = repo.hentBehandling(behandling.id)!!

            hentet.saksbehandler() shouldBe saksbehandler.saksbehandler()
        }
    }

    @Test
    fun `attesterer behandling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)

            val attestant = repo.attester(behandling.id, Attestant("kjella"))
            val hentet = repo.hentBehandling(behandling.id)!!

            hentet.attestant() shouldBe attestant.attestant()
        }
    }

    @Test
    fun `oppdater behandlingstatus`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)

            behandling.status() shouldBe Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

            val oppdatertStatus =
                repo.oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.BEREGNET_INNVILGET)
            val hentet = repo.hentBehandling(behandling.id)

            hentet!!.status() shouldBe oppdatertStatus.status()
        }
    }
}
