package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.AVSLÅTT
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.BEREGNET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.OPPRETTET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.SIMULERT
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.TIL_ATTESTERING
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.VILKÅRSVURDERT
import no.nav.su.se.bakover.domain.Vilkårsvurdering.Status.IKKE_OK
import no.nav.su.se.bakover.domain.Vilkårsvurdering.Status.IKKE_VURDERT
import no.nav.su.se.bakover.domain.Vilkårsvurdering.Status.OK
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
        behandling = createBehandling(id1, OPPRETTET)
    }

    @Test
    fun equals() {
        val a = createBehandling(id1, status = VILKÅRSVURDERT)
        val b = Behandling(
            id1,
            vilkårsvurderinger = mutableListOf(
                Vilkårsvurdering(
                    vilkår = Vilkår.UFØRHET,
                    begrunnelse = "",
                    status = OK
                )
            ),
            søknad = søknad,
            status = VILKÅRSVURDERT,
            sakId = id1
        )
        val c = createBehandling(id2, status = VILKÅRSVURDERT)
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(b, c)
        assertNotEquals(a, null)
        assertNotEquals(a, Object())
    }

    @Test
    fun hashcode() {
        val a = createBehandling(id1, status = VILKÅRSVURDERT)
        val b = Behandling(
            id1,
            vilkårsvurderinger = mutableListOf(
                Vilkårsvurdering(
                    vilkår = Vilkår.UFØRHET,
                    begrunnelse = "",
                    status = OK
                )
            ),
            søknad = søknad,
            status = VILKÅRSVURDERT,
            sakId = id1
        )
        val c = createBehandling(id2, status = VILKÅRSVURDERT)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a.hashCode(), c.hashCode())
        val hashSet = hashSetOf(a, b, c)
        assertEquals(2, hashSet.size)
        assertTrue(hashSet.contains(a))
        assertTrue(hashSet.contains(c))
    }

    @Nested
    inner class Opprettet {
        private lateinit var opprettet: Behandling

        @BeforeEach
        fun beforeEach() {
            opprettet = createBehandling(id1, OPPRETTET)
            opprettet.status() shouldBe OPPRETTET
        }

        @Test
        fun `legal operations`() {
            opprettet.opprettVilkårsvurderinger()
            opprettet.oppdaterVilkårsvurderinger(extractVilkårsvurderinger(opprettet).withStatus(OK))
        }

        @Test
        fun `should create all vilkårsvurderinger`() {
            val expected = listOf(
                Vilkår.UFØRHET,
                Vilkår.FLYKTNING,
                Vilkår.OPPHOLDSTILLATELSE,
                Vilkår.PERSONLIG_OPPMØTE,
                Vilkår.FORMUE,
                Vilkår.BOR_OG_OPPHOLDER_SEG_I_NORGE
            )
            opprettet.opprettVilkårsvurderinger()

            observer.opprettetVilkårsvurdering.first shouldBe id1
            observer.opprettetVilkårsvurdering.second.size shouldBe 6
            observer.opprettetVilkårsvurdering.second.map { it.toDto().vilkår } shouldContainExactly expected
        }

        @Test
        fun `should update vilkårsvurderinger`() {
            opprettet.opprettVilkårsvurderinger()
            val expected = extractVilkårsvurderinger(opprettet).withStatus(OK)
            opprettet.oppdaterVilkårsvurderinger(expected)
            opprettet.status() shouldBe VILKÅRSVURDERT
            observer.oppdatertStatus shouldBe opprettet.status()
            observer.oppdaterteVilkårsvurderinger.map { it.second } shouldContainAll expected
        }

        @Test
        fun `transition to Vilkårsvurdert`() {
            opprettet.opprettVilkårsvurderinger()
            opprettet.oppdaterVilkårsvurderinger(extractVilkårsvurderinger(opprettet).withStatus(OK))
            opprettet.status() shouldBe VILKÅRSVURDERT
            observer.oppdatertStatus shouldBe opprettet.status()
        }

        @Test
        fun `transition to Avslått`() {
            opprettet.opprettVilkårsvurderinger()
            opprettet.oppdaterVilkårsvurderinger(extractVilkårsvurderinger(opprettet).withStatus(IKKE_OK))
            opprettet.status() shouldBe AVSLÅTT
            observer.oppdatertStatus shouldBe opprettet.status()
        }

        @Test
        fun `dont transition if vilkårsvurdering not completed`() {
            opprettet.opprettVilkårsvurderinger()
            opprettet.oppdaterVilkårsvurderinger(extractVilkårsvurderinger(opprettet).withStatus(IKKE_VURDERT))
            opprettet.status() shouldBe OPPRETTET
        }

        @Test
        fun `should only create vilkårsvurderinger once`() {
            opprettet.opprettVilkårsvurderinger()
            assertThrows<Behandling.TilstandException> { opprettet.opprettVilkårsvurderinger() }
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { opprettet.opprettBeregning(1.januar(2020), 31.desember(2020)) }
                .also {
                    it.msg shouldContain "Illegal operation"
                    it.msg shouldContain "opprettBeregning"
                    it.msg shouldContain "state: OPPRETTET"
                }
            assertThrows<Behandling.TilstandException> {
                opprettet.leggTilUtbetaling(
                    Utbetaling(
                        oppdragId = UUID30.randomUUID(),
                        behandlingId = UUID.randomUUID(),
                        utbetalingslinjer = emptyList()
                    )
                )
            }
            assertThrows<Behandling.TilstandException> { opprettet.sendTilAttestering() }
        }
    }

    @Nested
    inner class Vilkårsvurdert {
        private lateinit var vilkårsvurdert: Behandling

        @BeforeEach
        fun beforeEach() {
            vilkårsvurdert = createBehandling(id1, OPPRETTET)
                .opprettVilkårsvurderinger()
            vilkårsvurdert.oppdaterVilkårsvurderinger(
                extractVilkårsvurderinger(vilkårsvurdert).withStatus(
                    OK
                )
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT
            observer.oppdatertStatus shouldBe vilkårsvurdert.status()
        }

        @Test
        fun `legal operations`() {
            vilkårsvurdert.opprettBeregning(1.januar(2020), 31.desember(2020))
            vilkårsvurdert.status() shouldBe BEREGNET
        }

        @Test
        fun `should create beregning`() {
            val fom = 1.januar(2020)
            val tom = 31.desember(2020)
            vilkårsvurdert.opprettBeregning(
                fom = fom,
                tom = tom,
                sats = Sats.LAV
            )
            observer.opprettetBeregning.first shouldBe id1
            observer.opprettetBeregning.second shouldNotBe null
            val beregning = observer.opprettetBeregning.second
            beregning.fom shouldBe fom
            beregning.tom shouldBe tom
            beregning.sats shouldBe Sats.LAV
        }

        @Test
        fun `skal kunne vilkårsvudere på nytt`() {
            vilkårsvurdert.oppdaterVilkårsvurderinger(
                extractVilkårsvurderinger(vilkårsvurdert).withStatus(OK)
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.opprettVilkårsvurderinger() }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.leggTilUtbetaling(
                    Utbetaling(
                        oppdragId = UUID30.randomUUID(),
                        behandlingId = UUID.randomUUID(),
                        utbetalingslinjer = emptyList()
                    )
                )
            }
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.sendTilAttestering() }
        }
    }

    @Nested
    inner class Beregnet {
        private lateinit var beregnet: Behandling

        @BeforeEach
        fun beforeEach() {
            beregnet = createBehandling(id1, OPPRETTET)
                .opprettVilkårsvurderinger()
            beregnet.oppdaterVilkårsvurderinger(
                extractVilkårsvurderinger(beregnet).withStatus(
                    OK
                )
            )
            beregnet.opprettBeregning(1.januar(2020), 31.desember(2020))
            beregnet.status() shouldBe BEREGNET
            observer.oppdatertStatus shouldBe beregnet.status()
        }

        @Test
        fun `skal kunne simuleres`() {
            beregnet.leggTilUtbetaling(
                Utbetaling(
                    oppdragId = UUID30.randomUUID(),
                    behandlingId = UUID.randomUUID(),
                    utbetalingslinjer = emptyList()
                )
            )
            beregnet.status() shouldBe SIMULERT
        }

        @Test
        fun `skal kunne beregne på nytt`() {
            beregnet.opprettBeregning(1.januar(2020), 31.desember(2020))
            beregnet.status() shouldBe BEREGNET
        }

        @Test
        fun `skal kunne vilkårsvudere på nytt`() {
            beregnet.oppdaterVilkårsvurderinger(
                extractVilkårsvurderinger(beregnet).withStatus(OK)
            )
            beregnet.status() shouldBe VILKÅRSVURDERT
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { beregnet.opprettVilkårsvurderinger() }
            assertThrows<Behandling.TilstandException> { beregnet.sendTilAttestering() }
        }
    }

    @Nested
    inner class Simulert {
        private lateinit var simulert: Behandling

        @BeforeEach
        fun beforeEach() {
            simulert = createBehandling(id1, OPPRETTET)
                .opprettVilkårsvurderinger()
            simulert.oppdaterVilkårsvurderinger(
                extractVilkårsvurderinger(simulert).withStatus(
                    OK
                )
            )
            simulert.opprettBeregning(1.januar(2020), 31.desember(2020))
            simulert.leggTilUtbetaling(
                Utbetaling(
                    oppdragId = UUID30.randomUUID(),
                    behandlingId = UUID.randomUUID(),
                    utbetalingslinjer = emptyList()
                )
            )
            simulert.status() shouldBe SIMULERT
            observer.oppdatertStatus shouldBe simulert.status()
        }

        @Test
        fun `skal kunne attestere`() {
            simulert.sendTilAttestering()
            simulert.status() shouldBe TIL_ATTESTERING
        }

        @Test
        fun `skal kunne beregne på nytt`() {
            simulert.opprettBeregning(1.januar(2020), 31.desember(2020))
            simulert.status() shouldBe BEREGNET
        }

        @Test
        fun `skal kunne vilkårsvurdere på nytt`() {
            simulert.oppdaterVilkårsvurderinger(
                extractVilkårsvurderinger(simulert).withStatus(OK)
            )
            simulert.status() shouldBe VILKÅRSVURDERT
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { simulert.opprettVilkårsvurderinger() }

            assertThrows<Behandling.TilstandException> {
                simulert.leggTilUtbetaling(
                    Utbetaling(
                        oppdragId = UUID30.randomUUID(),
                        behandlingId = UUID.randomUUID(),
                        utbetalingslinjer = emptyList()
                    )
                )
            }
        }
    }

    @Nested
    inner class Avslått {
        private lateinit var avslått: Behandling

        @BeforeEach
        fun beforeEach() {
            avslått = createBehandling(id1, OPPRETTET)
                .opprettVilkårsvurderinger()
            avslått.oppdaterVilkårsvurderinger(
                extractVilkårsvurderinger(avslått).withStatus(
                    IKKE_OK
                )
            )
            avslått.status() shouldBe AVSLÅTT
            observer.oppdatertStatus shouldBe avslått.status()
        }

        @Test
        fun `legal operations`() {
            avslått.oppdaterVilkårsvurderinger(extractVilkårsvurderinger(avslått).withStatus(OK))
            avslått.status() shouldBe VILKÅRSVURDERT
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { avslått.opprettVilkårsvurderinger() }
            assertThrows<Behandling.TilstandException> {
                avslått.opprettBeregning(1.januar(2020), 31.desember(2020))
            }
            assertThrows<Behandling.TilstandException> {
                avslått.leggTilUtbetaling(
                    Utbetaling(
                        oppdragId = UUID30.randomUUID(),
                        behandlingId = UUID.randomUUID(),
                        utbetalingslinjer = emptyList()
                    )
                )
            }
        }
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
        var oppdaterteVilkårsvurderinger: MutableList<Pair<UUID, Vilkårsvurdering>> = mutableListOf()

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

        override fun attester(behandlingId: UUID, attestant: Attestant): Attestant {
            return attestant
        }

        override fun oppdaterVilkårsvurdering(vilkårsvurdering: Vilkårsvurdering): Vilkårsvurdering {
            oppdaterteVilkårsvurderinger.add(vilkårsvurdering.id to vilkårsvurdering)
            return vilkårsvurdering
        }
    }

    private fun createBehandling(
        id: UUID,
        status: BehandlingsStatus
    ) = Behandling(
        id = id,
        søknad = søknad,
        status = status,
        sakId = id1
    ).also {
        observer = DummyObserver()
        it.addObserver(observer)
    }
}
