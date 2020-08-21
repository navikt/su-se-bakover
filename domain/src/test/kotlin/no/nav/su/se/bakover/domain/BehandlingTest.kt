package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling.BehandlingState.BehandlingStateException
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus
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
        behandling.status() shouldBe BehandlingsStatus.OPPRETTET
        val tilAttestering = behandling.sendTilAttestering()
        tilAttestering.status() shouldBe BehandlingsStatus.TIL_ATTESTERING
        tilAttestering.status() shouldBe observer.oppdatertStatus
    }

    @Test
    fun `should throw exception when illegal operations on state TIL_ATTESTERING`() {
        behandling = createBehandling(id = id1, status = BehandlingsStatus.TIL_ATTESTERING)

        assertThrows<BehandlingStateException> { behandling.opprettVilkårsvurderinger() }
        assertThrows<BehandlingStateException> { behandling.oppdaterVilkårsvurderinger(emptyList()) }
        assertThrows<BehandlingStateException> { behandling.opprettBeregning(1.januar(2020), 31.desember(2020)) }
        assertThrows<BehandlingStateException> {
            behandling.addOppdrag(
                Oppdrag(
                    sakId = UUID.randomUUID(),
                    behandlingId = UUID.randomUUID(),
                    oppdragslinjer = emptyList()
                )
            )
        }
        assertThrows<BehandlingStateException> { behandling.sendTilAttestering() }
    }

    @Test
    fun `should throw exception when illegal operations on state TIL_BEHANDLING`() {
        assertDoesNotThrow { behandling.opprettVilkårsvurderinger() }
        assertDoesNotThrow { behandling.oppdaterVilkårsvurderinger(emptyList()) }
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
    fun `denne testen burde ihvertfall eksistert dersom utledStatus() har noe å si fra frontend KREMT JOHN`() {
        // Initial state
        behandling.status() shouldBe BehandlingsStatus.OPPRETTET
        behandling.toDto().status shouldBe BehandlingsStatus.OPPRETTET

        val vilkårsvurderinger = behandling.opprettVilkårsvurderinger()
            .toDto().vilkårsvurderinger.map {
                Vilkårsvurdering(
                    id = it.id,
                    opprettet = it.opprettet,
                    vilkår = it.vilkår,
                    begrunnelse = it.begrunnelse,
                    status = it.status
                )
            }
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
        // innvilget.status() shouldBe BehandlingsStatus.INNVILGET
        // innvilget.toDto().status shouldBe BehandlingsStatus.INNVILGET

        // Transition to til attestering
        val tilAttestering = behandling.sendTilAttestering()
        tilAttestering.status() shouldBe BehandlingsStatus.TIL_ATTESTERING
        tilAttestering.toDto().status shouldBe BehandlingsStatus.TIL_ATTESTERING
    }

    private fun List<Vilkårsvurdering>.withStatus(status: Vilkårsvurdering.Status) = map {
        Vilkårsvurdering(
            id = it.id,
            opprettet = it.opprettet,
            vilkår = it.vilkår,
            begrunnelse = status.name,
            status = status
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
