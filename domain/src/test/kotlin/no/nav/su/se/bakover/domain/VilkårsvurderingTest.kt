package no.nav.su.se.bakover.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VilkårsvurderingTest {

    private val id1 = UUID.randomUUID()
    private val id2 = UUID.randomUUID()

    @Test
    fun equals() {
        val vilkår =
            Vilkårsvurdering(id1, vilkår = Vilkår.UFØRHET, begrunnelse = "", status = Vilkårsvurdering.Status.IKKE_OK)
        val vilkårEndret =
            Vilkårsvurdering(id1, vilkår = Vilkår.UFØRHET, begrunnelse = "asdgdg", status = Vilkårsvurdering.Status.OK)

        val annetVilkår =
            Vilkårsvurdering(id2, vilkår = Vilkår.UFØRHET, begrunnelse = "", status = Vilkårsvurdering.Status.IKKE_OK)

        assertEquals(vilkår, vilkårEndret)
        assertNotEquals(vilkår, annetVilkår)

        assertNotEquals(vilkår, null)
        assertEquals(vilkår, vilkår)
    }

    @Test
    fun hashcode() {
        val a = Vilkårsvurdering(id1, vilkår = Vilkår.UFØRHET, begrunnelse = "", status = Vilkårsvurdering.Status.OK)
        val b = Vilkårsvurdering(
            id1,
            vilkår = Vilkår.UFØRHET,
            begrunnelse = "begrunnelse",
            status = Vilkårsvurdering.Status.IKKE_OK
        )
        val c = Vilkårsvurdering(
            id2,
            vilkår = Vilkår.UFØRHET,
            begrunnelse = "begrunnelse",
            status = Vilkårsvurdering.Status.IKKE_OK
        )
        assertEquals(a.hashCode(), a.hashCode())
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a.hashCode(), c.hashCode())
        val hashSet = hashSetOf(a, b, c)
        assertEquals(2, hashSet.size)
        assertTrue(hashSet.contains(a))
        assertTrue(hashSet.contains(c))
    }
}
