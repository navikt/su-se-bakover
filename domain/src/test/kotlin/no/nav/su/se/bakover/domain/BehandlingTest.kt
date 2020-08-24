package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling.Status
import no.nav.su.se.bakover.domain.Behandling.Status.Avslått
import no.nav.su.se.bakover.domain.Behandling.Status.BehandlingsStatus
import no.nav.su.se.bakover.domain.Behandling.Status.Beregnet
import no.nav.su.se.bakover.domain.Behandling.Status.Innvilget
import no.nav.su.se.bakover.domain.Behandling.Status.Opprettet
import no.nav.su.se.bakover.domain.Behandling.Status.Simulert
import no.nav.su.se.bakover.domain.Behandling.Status.TilAttestering
import no.nav.su.se.bakover.domain.Behandling.Status.Vilkårsvurdert
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class BehandlingTest {

    private val id1 = UUID.randomUUID()
    private val id2 = UUID.randomUUID()
    private val søknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build())

    private lateinit var behandling: Behandling
    private lateinit var observer: DummyObserver

    @BeforeEach
    fun beforeEach() {
        behandling = createBehandling(id1, BehandlingsStatus.OPPRETTET)
    }

    @Test
    fun equals() {
        val a = createBehandling(id1, status = BehandlingsStatus.VILKÅRSVURDERT)
        val b = Behandling(
            id1,
            vilkårsvurderinger = mutableListOf(
                Vilkårsvurdering(
                    vilkår = Vilkår.UFØRHET,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.OK
                )
            ),
            søknad = søknad,
            status = BehandlingsStatus.VILKÅRSVURDERT
        )
        val c = createBehandling(id2, status = BehandlingsStatus.VILKÅRSVURDERT)
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(b, c)
        assertNotEquals(a, null)
        assertNotEquals(a, Object())
    }

    @Test
    fun hashcode() {
        val a = createBehandling(id1, status = BehandlingsStatus.VILKÅRSVURDERT)
        val b = Behandling(
            id1,
            vilkårsvurderinger = mutableListOf(
                Vilkårsvurdering(
                    vilkår = Vilkår.UFØRHET,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.OK
                )
            ),
            søknad = søknad,
            status = BehandlingsStatus.VILKÅRSVURDERT
        )
        val c = createBehandling(id2, status = BehandlingsStatus.VILKÅRSVURDERT)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a.hashCode(), c.hashCode())
        val hashSet = hashSetOf(a, b, c)
        assertEquals(2, hashSet.size)
        assertTrue(hashSet.contains(a))
        assertTrue(hashSet.contains(c))
    }

    @Test
    fun `burde opprette alle vilkårsvurderinger`() {
        val expected = listOf(
            Vilkår.UFØRHET,
            Vilkår.FLYKTNING,
            Vilkår.OPPHOLDSTILLATELSE,
            Vilkår.PERSONLIG_OPPMØTE,
            Vilkår.FORMUE,
            Vilkår.BOR_OG_OPPHOLDER_SEG_I_NORGE
        )
        behandling.opprettVilkårsvurderinger()

        observer.opprettetVilkårsvurdering.first shouldBe id1
        observer.opprettetVilkårsvurdering.second.size shouldBe 6
        observer.opprettetVilkårsvurdering.second.map { it.toDto().vilkår } shouldContainExactly expected
    }

    @Test
    fun `opprette beregning`() {
        val fom = 1.januar(2020)
        val tom = 31.desember(2020)
        behandling.oppdaterVilkårsvurderinger(extractVilkårsvurderinger(behandling).withStatus(Vilkårsvurdering.Status.OK))
        behandling.opprettBeregning(
            fom = fom,
            tom = tom,
            sats = Sats.LAV
        )
        observer.opprettetBeregning.first shouldBe id1
        observer.opprettetBeregning.second shouldNotBe null
        val dto = observer.opprettetBeregning.second.toDto()
        dto.fom shouldBe fom
        dto.tom shouldBe tom
        dto.sats shouldBe Sats.LAV
    }

    @Test
    fun `should update status when sent to attestering`() {
        val simulert = createBehandling(id1, BehandlingsStatus.SIMULERT)
        simulert.status() shouldBe BehandlingsStatus.SIMULERT
        val tilAttestering = simulert.sendTilAttestering()
        tilAttestering.status() shouldBe BehandlingsStatus.TIL_ATTESTERING
        tilAttestering.status() shouldBe observer.oppdatertStatus
    }

    @Test
    fun `should throw exception when illegal state transition`() {
        behandling.status() shouldBe BehandlingsStatus.OPPRETTET
        assertThrows<Behandling.BehandlingStateException> {
            behandling.sendTilAttestering()
        }
    }

    @Test
    fun `should throw exception when illegal operations on state TIL_ATTESTERING`() {
        behandling = createBehandling(id = id1, status = BehandlingsStatus.TIL_ATTESTERING)

        assertThrows<Behandling.BehandlingStateException> { behandling.opprettVilkårsvurderinger() }
        assertThrows<Behandling.BehandlingStateException> { behandling.oppdaterVilkårsvurderinger(emptyList()) }
        assertThrows<Behandling.BehandlingStateException> {
            behandling.opprettBeregning(
                1.januar(2020),
                31.desember(2020)
            )
        }
        assertThrows<Behandling.BehandlingStateException> {
            behandling.addOppdrag(
                Oppdrag(
                    sakId = UUID.randomUUID(),
                    behandlingId = UUID.randomUUID(),
                    oppdragslinjer = emptyList()
                )
            )
        }
        assertThrows<Behandling.BehandlingStateException> { behandling.sendTilAttestering() }
    }

    @Test
    fun `should throw exception when illegal operations on state TIL_BEHANDLING`() {
        lateinit var vilkårsvurderinger: List<Vilkårsvurdering>
        assertDoesNotThrow { vilkårsvurderinger = extractVilkårsvurderinger(behandling.opprettVilkårsvurderinger()) }
        assertDoesNotThrow { behandling.oppdaterVilkårsvurderinger(vilkårsvurderinger.withStatus(Vilkårsvurdering.Status.OK)) }
        assertDoesNotThrow { behandling.opprettBeregning(1.januar(2020), 31.desember(2020)) }
        assertDoesNotThrow {
            behandling.addOppdrag(
                Oppdrag(
                    sakId = UUID.randomUUID(),
                    behandlingId = UUID.randomUUID(),
                    oppdragslinjer = emptyList()
                )
            )
        }
        assertDoesNotThrow { behandling.sendTilAttestering() }
    }

    @Test
    fun `state transitions`() {
        // Initial state
        behandling.status() shouldBe BehandlingsStatus.OPPRETTET
        behandling.toDto().status shouldBe BehandlingsStatus.OPPRETTET

        val vilkårsvurderinger = extractVilkårsvurderinger(behandling.opprettVilkårsvurderinger())

        val ikkeVurdert =
            behandling.oppdaterVilkårsvurderinger(vilkårsvurderinger.withStatus(Vilkårsvurdering.Status.IKKE_VURDERT))

        // State unchanged when vilkårsvurdering not completed
        ikkeVurdert.status() shouldBe BehandlingsStatus.OPPRETTET
        ikkeVurdert.toDto().status shouldBe BehandlingsStatus.OPPRETTET

        val avslått =
            behandling.oppdaterVilkårsvurderinger(vilkårsvurderinger.withStatus(Vilkårsvurdering.Status.IKKE_OK))

        // State is avslått when vilkårsvurderinger not OK
        avslått.status() shouldBe BehandlingsStatus.AVSLÅTT
        avslått.toDto().status shouldBe BehandlingsStatus.AVSLÅTT

        // Transition to state vilkårsvurdert
        val vilkårsvurdert =
            behandling.oppdaterVilkårsvurderinger(vilkårsvurderinger.withStatus(Vilkårsvurdering.Status.OK))
        vilkårsvurdert.status() shouldBe BehandlingsStatus.VILKÅRSVURDERT
        vilkårsvurdert.toDto().status shouldBe BehandlingsStatus.VILKÅRSVURDERT

        // Transition to beregnet
        val beregnet = behandling.opprettBeregning(1.januar(2020), 31.desember(2020))
        beregnet.status() shouldBe BehandlingsStatus.BEREGNET
        beregnet.toDto().status shouldBe BehandlingsStatus.BEREGNET

        // Transition to simulert
        val simulert = behandling.addOppdrag(
            Oppdrag(
                sakId = UUID.randomUUID(),
                behandlingId = behandling.id,
                oppdragslinjer = emptyList()
            )
        )
        simulert.status() shouldBe BehandlingsStatus.SIMULERT
        simulert.toDto().status shouldBe BehandlingsStatus.SIMULERT

        // TODO what does this status represent?
        // val innvilget = behandling.innvilg()?
        // innvilget.status() shouldBe INNVILGET
        // innvilget.toDto().status shouldBe INNVILGET

        // Transition to til attestering
        val tilAttestering = behandling.sendTilAttestering()
        tilAttestering.status() shouldBe BehandlingsStatus.TIL_ATTESTERING
        tilAttestering.toDto().status shouldBe BehandlingsStatus.TIL_ATTESTERING
    }

    @Test
    fun `valid transitions`() {
        Opprettet.assertTransition(true, Vilkårsvurdert)
        Vilkårsvurdert.assertTransition(true, Opprettet, Beregnet)
        Beregnet.assertTransition(true, Vilkårsvurdert, Simulert)
        Simulert.assertTransition(true, Beregnet, TilAttestering)
        Innvilget.assertTransition(true)
        Avslått.assertTransition(true, Vilkårsvurdert)

        Opprettet.assertTransition(false, Beregnet, Simulert)
        Vilkårsvurdert.assertTransition(false, Simulert, TilAttestering)
        Beregnet.assertTransition(false, Opprettet, TilAttestering)
        Avslått.assertTransition(false, Innvilget)
    }

    private fun Status.assertTransition(valid: Boolean, vararg status: Status) =
        status.forEach { this.validTransition(it) shouldBe valid }

    private fun List<Vilkårsvurdering>.withStatus(status: Vilkårsvurdering.Status) = map {
        Vilkårsvurdering(
            id = it.id,
            opprettet = it.opprettet,
            vilkår = it.vilkår,
            begrunnelse = status.name,
            status = status
        )
    }

    private fun extractVilkårsvurderinger(behandling: Behandling) =
        behandling.toDto().vilkårsvurderinger.map {
            Vilkårsvurdering(
                id = it.id,
                opprettet = it.opprettet,
                vilkår = it.vilkår,
                begrunnelse = it.begrunnelse,
                status = it.status
            )
        }

    private class DummyObserver : BehandlingPersistenceObserver, VilkårsvurderingPersistenceObserver {
        lateinit var opprettetVilkårsvurdering: Pair<UUID, List<Vilkårsvurdering>>
        lateinit var opprettetBeregning: Pair<UUID, Beregning>
        lateinit var oppdatertStatus: BehandlingsStatus

        override fun opprettVilkårsvurderinger(
            behandlingId: UUID,
            vilkårsvurderinger: List<Vilkårsvurdering>
        ): List<Vilkårsvurdering> {
            vilkårsvurderinger.forEach { it.addObserver(this) }
            opprettetVilkårsvurdering = behandlingId to vilkårsvurderinger
            return opprettetVilkårsvurdering.second
        }

        override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
            opprettetBeregning = behandlingId to beregning
            return opprettetBeregning.second
        }

        override fun oppdaterBehandlingStatus(
            behandlingId: UUID,
            status: BehandlingsStatus
        ): BehandlingsStatus {
            oppdatertStatus = status
            return status
        }

        override fun oppdaterVilkårsvurdering(vilkårsvurdering: Vilkårsvurdering): Vilkårsvurdering {
            return vilkårsvurdering
        }
    }

    private fun createBehandling(
        id: UUID,
        status: BehandlingsStatus
    ) = Behandling(
        id = id,
        søknad = søknad,
        status = status
    ).also {
        observer = DummyObserver()
        it.addObserver(observer)
    }
}
