package no.nav.su.se.bakover.domain.beregning.fradrag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FradragstypeTest {

    @Test
    fun `kaster dersom type ikke er Annet, og typen er spesifisert`() {
        assertThrows<IllegalArgumentException> {
            Fradragstype(F.Kontantstøtte, "jeg spesifiserer typen uten at den er annet")
        }
    }

    @Test
    fun `kaster dersom type er Annet, og typen er ikke spesifisert`() {
        assertThrows<IllegalArgumentException> { Fradragstype(F.Annet, null) }
    }
}
