package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
        behandling.addObserver(object : BehandlingPersistenceObserver {
            override fun opprettVilkårsvurderinger(
                behandlingId: UUID,
                vilkårsvurderinger: List<Vilkårsvurdering>
            ): List<Vilkårsvurdering> {
                vilkårsvurderinger.map { it.toDto().vilkår } shouldContainExactly expected
                return vilkårsvurderinger
            }
        })
        behandling.opprettVilkårsvurderinger()
    }
}
