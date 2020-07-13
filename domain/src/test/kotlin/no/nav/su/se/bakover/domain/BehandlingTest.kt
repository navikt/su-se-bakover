package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.UUID

internal class BehandlingTest {

    private val id1 = UUID.randomUUID()
    private val id2 = UUID.randomUUID()
    private val søknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build())

    @Test
    fun equals() {
        val a = Behandling(id1, søknad = søknad)
        val b = Behandling(
            id1,
            vilkårsvurderinger = mutableListOf(
                Vilkårsvurdering(
                    vilkår = Vilkår.UFØRHET,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.OK
                )
            ),
            søknad = søknad
        )
        val c = Behandling(id2, søknad = søknad)
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(b, c)
        assertNotEquals(a, null)
        assertNotEquals(a, Object())
    }

    @Test
    fun hashcode() {

        val a = Behandling(id1, søknad = søknad)
        val b = Behandling(
            id1,
            vilkårsvurderinger = mutableListOf(
                Vilkårsvurdering(
                    vilkår = Vilkår.UFØRHET,
                    begrunnelse = "",
                    status = Vilkårsvurdering.Status.OK
                )
            ),
            søknad = søknad
        )
        val c = Behandling(id2, søknad = søknad)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a.hashCode(), c.hashCode())
        val hashSet = hashSetOf(a, b, c)
        assertEquals(2, hashSet.size)
        assertTrue(hashSet.contains(a))
        assertTrue(hashSet.contains(c))
    }

    @Test
    fun `burde opprette alle vilkårsvurderinger`() {
        val behandling = Behandling(id = id1, søknad = søknad)
        val expected = listOf(
            Vilkår.UFØRHET,
            Vilkår.FLYKTNING,
            Vilkår.OPPHOLDSTILLATELSE,
            Vilkår.PERSONLIG_OPPMØTE,
            Vilkår.FORMUE,
            Vilkår.BOR_OG_OPPHOLDER_SEG_I_NORGE
        )
        val observer = DummyObserver()
        behandling.addObserver(observer)
        behandling.opprettVilkårsvurderinger()

        observer.opprettetVilkårsvurdering.first shouldBe id1
        observer.opprettetVilkårsvurdering.second.size shouldBe 6
        observer.opprettetVilkårsvurdering.second.map { it.toDto().vilkår } shouldContainExactly expected
    }

    @Test
    fun `opprette beregning`() {
        val behandling = Behandling(id = id1, søknad = søknad)
        val observer = DummyObserver()
        behandling.addObserver(observer)
        val fom = LocalDate.of(2020, Month.JANUARY, 1)
        val tom = LocalDate.of(2020, Month.DECEMBER, 31)
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

    private class DummyObserver() : BehandlingPersistenceObserver {
        lateinit var opprettetVilkårsvurdering: Pair<UUID, List<Vilkårsvurdering>>
        lateinit var opprettetBeregning: Pair<UUID, Beregning>

        override fun opprettVilkårsvurderinger(
            behandlingId: UUID,
            vilkårsvurderinger: List<Vilkårsvurdering>
        ): List<Vilkårsvurdering> {
            opprettetVilkårsvurdering = behandlingId to vilkårsvurderinger
            return opprettetVilkårsvurdering.second
        }

        override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
            opprettetBeregning = behandlingId to beregning
            return opprettetBeregning.second
        }
    }
}
