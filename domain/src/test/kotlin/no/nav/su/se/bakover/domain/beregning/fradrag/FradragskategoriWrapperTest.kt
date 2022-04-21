package no.nav.su.se.bakover.domain.beregning.fradrag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FradragskategoriWrapperTest {
    @Test
    fun `kaster dersom kategorien ikke er Annet, og kategorien er spesifisert`() {
        assertThrows<IllegalArgumentException> {
            FradragskategoriWrapper(Fradragskategori.Kontantstøtte, "jeg spesifiserer typen uten at den er annet")
        }
    }

    @Test
    fun `kaster dersom kategorien er Annet, og kategorien er ikke spesifisert`() {
        assertThrows<IllegalArgumentException> { FradragskategoriWrapper(Fradragskategori.Annet, null) }
    }
}
