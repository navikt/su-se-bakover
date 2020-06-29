package no.nav.su.se.bakover.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class VilkårsvurderingTest {
    @Test
    fun `Equality`() {
        val vilkår = Vilkårsvurdering(1, Vilkår.UFØRE, "", Vilkårsvurdering.Status.IKKE_OK)
        val vilkårEndret = Vilkårsvurdering(1, Vilkår.UFØRE, "asdgdg", Vilkårsvurdering.Status.OK)

        val annetVilkår = Vilkårsvurdering(2, Vilkår.UFØRE, "", Vilkårsvurdering.Status.IKKE_OK)

        assertEquals(vilkår, vilkårEndret)
        assertNotEquals(vilkår, annetVilkår)

        assertNotEquals(vilkår, null)
        assertEquals(vilkår, vilkår)
    }
}