package no.nav.su.se.bakover.database

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.BehandlingPersistenceObserver
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistenceObserverException
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.SakPersistenceObserver
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Vilkår.FLYKTNING
import no.nav.su.se.bakover.domain.Vilkår.UFØRHET
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingPersistenceObserver
import no.nav.su.se.bakover.domain.VoidObserver
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.OppdragPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class DatabaseRepoTest {

    private val repo = DatabaseRepo(EmbeddedDatabase.instance())
    private val FNR = FnrGenerator.random()

    @Test
    fun `unknown entities`() {
        withMigratedDb {
            assertNull(repo.hentSak(FnrGenerator.random()))
            assertNull(repo.hentSak(UUID.randomUUID()))
            assertTrue(repo.hentVilkårsvurderinger(UUID.randomUUID()).isEmpty())
            assertNull(repo.hentBehandling(UUID.randomUUID()))
            assertNull(repo.hentSøknad(UUID.randomUUID()))
        }
    }

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb {
            val opprettet = repo.opprettSak(FNR)
            val hentetId = repo.hentSak(opprettet.id)
            val hentetFnr = repo.hentSak(FNR)

            opprettet shouldBe hentetId
            hentetId shouldBe hentetFnr

            opprettet.fnr shouldBe FNR

            listOf(opprettet, hentetId, hentetFnr).forEach {
                assertPersistenceObserverAssigned(it!!, sakPersistenceObserver())
            }
        }
    }

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            using(sessionOf(EmbeddedDatabase.instance())) {
                val sak = insertSak(FNR)
                val søknad = insertSøknad(sak.id)
                val hentetId = repo.hentSøknad(søknad.id)

                søknad shouldBe hentetId
                assertNoPersistenceObserverAssigned(søknad, voidObserver())
            }
        }
    }

    @Test
    fun `opprett og hent behandling`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            val hentet = repo.hentBehandling(behandling.id)

            behandling shouldBe hentet
            listOf(behandling, hentet).forEach {
                assertPersistenceObserverAssigned(it!!, behandlingPersistenceObserver())
            }
        }
    }

    @Test
    fun `oppdater behandlingstatus`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            behandling.status() shouldBe Behandling.BehandlingsStatus.VILKÅRSVURDERT

            val oppdatertStatus = repo.oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.BEREGNET)
            val hentet = repo.hentBehandling(behandling.id)

            hentet!!.status() shouldBe oppdatertStatus
        }
    }

    @Test
    fun `opprett og hent vilkårsvurderinger`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val vilkårsvurderinger = insertVilkårsvurderinger(behandling.id)

            val hentet = repo.hentVilkårsvurderinger(behandling.id)

            vilkårsvurderinger shouldBe hentet
            vilkårsvurderinger shouldHaveSize 2

            (vilkårsvurderinger + hentet).forEach {
                assertPersistenceObserverAssigned(it, vilkårsvurderingPersistenceObserver())
            }
        }
    }

    @Test
    fun `oppdater vilkårsvurderinger`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val vilkårsvurderinger = insertVilkårsvurderinger(behandling.id)
            val uførhet = vilkårsvurderinger.first { it.vilkår == UFØRHET }

            val oppdatert = repo.oppdaterVilkårsvurdering(
                Vilkårsvurdering(
                    id = uførhet.id,
                    vilkår = UFØRHET,
                    begrunnelse = "OK",
                    status = Vilkårsvurdering.Status.OK
                )
            )

            val hentet = repo.hentVilkårsvurderinger(behandling.id)
                .first { it.vilkår == UFØRHET }

            oppdatert shouldBe hentet
        }
    }

    @Test
    fun `opprett og hent beregning`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val beregning = insertBeregning(behandling.id)

            val hentet = repo.hentBeregninger(behandling.id)
                .first()

            beregning shouldBe hentet
            listOf(beregning, hentet).forEach {
                assertNoPersistenceObserverAssigned(it, voidObserver())
            }
        }
    }

    @Test
    fun `opprett og hent oppdrag`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)

            val oppdrag = insertOppdrag(sak.id, behandling.id)

            val hentet = repo.hentOppdrag(oppdrag.id)

            oppdrag shouldBe hentet
            oppdrag.sakId shouldBe sak.id
            oppdrag.behandlingId shouldBe behandling.id

            listOf(oppdrag, hentet).forEach {
                assertPersistenceObserverAssigned(it!!, oppdragPersistenceObserver())
            }
        }
    }

    @Test
    fun `opprett og hent oppdragslinjer`() {
        withMigratedDb {
            val sak = insertSak(FNR)
            val søknad = insertSøknad(sak.id)
            val behandling = insertBehandling(sak.id, søknad)
            val oppdrag = insertOppdrag(sak.id, behandling.id)
            val oppdragslinje = insertOppdragslinje(oppdrag.id)

            val hentet = repo.hentOppdrag(oppdrag.id)!!.oppdragslinjer.first()

            oppdragslinje shouldBe hentet
        }
    }

    private fun sakPersistenceObserver() = object : SakPersistenceObserver {
        override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad {
            throw NotImplementedError()
        }

        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling): Behandling {
            throw NotImplementedError()
        }

        override fun opprettOppdrag(oppdrag: Oppdrag): Oppdrag {
            throw NotImplementedError()
        }
    }

    private fun voidObserver() = object : VoidObserver {}

    private fun behandlingPersistenceObserver() = object : BehandlingPersistenceObserver {
        override fun opprettVilkårsvurderinger(
            behandlingId: UUID,
            vilkårsvurderinger: List<Vilkårsvurdering>
        ): List<Vilkårsvurdering> {
            throw NotImplementedError()
        }

        override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
            throw NotImplementedError()
        }

        override fun oppdaterBehandlingStatus(
            behandlingId: UUID,
            status: Behandling.BehandlingsStatus
        ): Behandling.BehandlingsStatus {
            throw NotImplementedError()
        }
    }

    private fun vilkårsvurderingPersistenceObserver() = object : VilkårsvurderingPersistenceObserver {
        override fun oppdaterVilkårsvurdering(vilkårsvurdering: Vilkårsvurdering): Vilkårsvurdering {
            throw NotImplementedError()
        }
    }

    private fun oppdragPersistenceObserver() = object : OppdragPersistenceObserver {
        override fun addSimulering(oppdragsId: UUID, simulering: Simulering): Simulering {
            throw NotImplementedError()
        }
    }

    private fun <T : PersistenceObserver> assertPersistenceObserverAssigned(
        target: PersistentDomainObject<T>,
        observer: T
    ) {
        assertThrows<PersistenceObserverException> {
            target.addObserver(observer)
        }.also {
            it.message shouldBe "There should only be one instance of type class no.nav.su.se.bakover.domain.PersistenceObserver assigned to an object!"
        }
    }

    private fun <T : PersistenceObserver> assertNoPersistenceObserverAssigned(
        target: PersistentDomainObject<T>,
        observer: T
    ) {
        assertDoesNotThrow {
            target.addObserver(observer)
        }
    }

    private fun insertSak(fnr: Fnr = FNR) = repo.opprettSak(fnr)
    private fun insertSøknad(sakId: UUID) = repo.opprettSøknad(
        sakId,
        Søknad(
            id = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )
    )

    private fun insertBehandling(sakId: UUID, søknad: Søknad) = repo.opprettSøknadsbehandling(
        sakId = sakId,
        behandling = Behandling(
            søknad = søknad,
            status = Behandling.BehandlingsStatus.VILKÅRSVURDERT
        )
    )

    private fun insertOppdrag(sakId: UUID, behandlingId: UUID) = repo.opprettOppdrag(
        oppdrag = Oppdrag(
            sakId = sakId,
            behandlingId = behandlingId,
            oppdragslinjer = emptyList()
        )
    )

    private fun insertOppdragslinje(oppdragId: UUID) = repo.opprettOppdragslinje(
        oppdragId = oppdragId,
        oppdragslinje = Oppdragslinje(
            fom = 1.januar(2020),
            tom = 31.desember(2020),
            forrigeOppdragslinjeId = null,
            beløp = 25000
        )
    )

    private fun insertBeregning(behandlingId: UUID) = repo.opprettBeregning(
        behandlingId = behandlingId,
        beregning = Beregning(
            fom = 1.januar(2020),
            tom = 31.desember(2020),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
    )

    private fun insertVilkårsvurderinger(behandlingId: UUID) =
        repo.opprettVilkårsvurderinger(
            behandlingId = behandlingId,
            vilkårsvurderinger = listOf(
                Vilkårsvurdering(
                    vilkår = UFØRHET,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.IKKE_VURDERT
                ),
                Vilkårsvurdering(
                    vilkår = FLYKTNING,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.IKKE_VURDERT
                )
            )
        )
}
