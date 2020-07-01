package no.nav.su.se.bakover.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BehandlingTest {
    @Test
    fun equals() {
        val a = Behandling(1, mutableListOf())
        val b = Behandling(1, mutableListOf(Vilkårsvurdering(1, Vilkår.UFØRHET, "", Vilkårsvurdering.Status.OK)))
        val c = Behandling(2, mutableListOf())
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(b, c)
        assertNotEquals(a, null)
        assertNotEquals(a, Object())
    }

    @Test
    fun hashcode() {
        val a = Behandling(1, mutableListOf())
        val b = Behandling(1, mutableListOf(Vilkårsvurdering(1, Vilkår.UFØRHET, "", Vilkårsvurdering.Status.OK)))
        val c = Behandling(2, mutableListOf())
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a.hashCode(), c.hashCode())
        val hashSet = hashSetOf(a, b, c)
        assertEquals(2, hashSet.size)
        assertTrue(hashSet.contains(a))
        assertTrue(hashSet.contains(c))
    }
}
