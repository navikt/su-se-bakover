package no.nav.su.se.bakover.database.behandling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import org.junit.jupiter.api.Test

internal class BehandlingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = BehandlingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent behandling`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.insertSak(FNR).toSak()
            val søknad: Søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)

            hentet shouldBe Behandling(
                id = nySøknadsbehandling.id,
                opprettet = nySøknadsbehandling.opprettet,
                fnr = FNR,
                søknad = søknad,
                sakId = sak.id
            )
        }
    }

    @Test
    fun `oppdater behandlingsinformasjon`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val oppdatert = repo.oppdaterBehandlingsinformasjon(
                nySøknadsbehandling.id,
                Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                        uføregrad = 40,
                        forventetInntekt = 200
                    )
                )
            )

            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            oppdatert.behandlingsinformasjon() shouldBe hentet.behandlingsinformasjon()
            nySøknadsbehandling.behandlingsinformasjon shouldNotBe hentet.behandlingsinformasjon()
        }
    }

    @Test
    fun `saksbehandle behandling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val saksbehandler = repo.settSaksbehandler(nySøknadsbehandling.id, NavIdentBruker.Saksbehandler("Per"))
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            hentet.saksbehandler() shouldBe saksbehandler.saksbehandler()
        }
    }

    @Test
    fun `attesterer behandling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            val attestant = repo.attester(nySøknadsbehandling.id, NavIdentBruker.Attestant("kjella"))
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)!!

            hentet.attestant() shouldBe attestant.attestant()
        }
    }

    @Test
    fun `oppdater behandlingstatus`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val nySøknadsbehandling = NySøknadsbehandling(
                sakId = sak.id,
                søknadId = søknad.id
            )

            repo.opprettSøknadsbehandling(nySøknadsbehandling)

            nySøknadsbehandling.status shouldBe Behandling.BehandlingsStatus.OPPRETTET

            val oppdatertStatus =
                repo.oppdaterBehandlingStatus(nySøknadsbehandling.id, Behandling.BehandlingsStatus.BEREGNET_INNVILGET)
            val hentet = repo.hentBehandling(nySøknadsbehandling.id)

            hentet!!.status() shouldBe oppdatertStatus.status()
        }
    }
}
