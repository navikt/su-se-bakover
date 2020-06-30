package no.nav.su.se.bakover.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VilkårsvurderingTest {
    @Test
    fun equals() {
        val vilkår = Vilkårsvurdering(1, Vilkår.UFØRE, "", Vilkårsvurdering.Status.IKKE_OK)
        val vilkårEndret = Vilkårsvurdering(1, Vilkår.UFØRE, "asdgdg", Vilkårsvurdering.Status.OK)

        val annetVilkår = Vilkårsvurdering(2, Vilkår.UFØRE, "", Vilkårsvurdering.Status.IKKE_OK)

        assertEquals(vilkår, vilkårEndret)
        assertNotEquals(vilkår, annetVilkår)

        assertNotEquals(vilkår, null)
        assertEquals(vilkår, vilkår)
    }

    @Test
    fun hashcode() {
        val a = Vilkårsvurdering(1, Vilkår.UFØRE, "", Vilkårsvurdering.Status.OK)
        val b = Vilkårsvurdering(1, Vilkår.UFØRE, "begrunnelse", Vilkårsvurdering.Status.IKKE_OK)
        val c = Vilkårsvurdering(5, Vilkår.UFØRE, "begrunnelse", Vilkårsvurdering.Status.IKKE_OK)
        assertEquals(a.hashCode(), a.hashCode())
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a.hashCode(), c.hashCode())
        val hashSet = hashSetOf(a, b, c)
        assertEquals(2, hashSet.size)
        assertTrue(hashSet.contains(a))
        assertTrue(hashSet.contains(c))
    }
}
