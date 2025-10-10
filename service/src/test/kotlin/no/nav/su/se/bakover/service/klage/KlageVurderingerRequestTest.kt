package no.nav.su.se.bakover.service.klage

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import org.junit.jupiter.api.Test

internal class KlageVurderingerRequestTest {

    @Test
    fun `gyldige hjemler`() {
        KlageVurderingerRequest.Oppretthold(
            listOf(
                "SU_PARAGRAF_3",
                "SU_PARAGRAF_4",
                "SU_PARAGRAF_5",
                "SU_PARAGRAF_6",
                "SU_PARAGRAF_8",
                "SU_PARAGRAF_9",
                "SU_PARAGRAF_10",
                "SU_PARAGRAF_11",
                "SU_PARAGRAF_12",
                "SU_PARAGRAF_13",
                "SU_PARAGRAF_17",
                "SU_PARAGRAF_18",
                "SU_PARAGRAF_21",
            ),
            klagenotat = "klagenotat",
        ).toDomain().shouldBeRight()
    }

    @Test
    fun `ugyldige hjemler`() {
        KlageVurderingerRequest.Oppretthold(
            listOf(
                "SU_PARAGRAF_0",
                "SU_PARAGRAF_1",
                "SU_PARAGRAF_2",
                "SU_PARAGRAF_7",
                "SU_PARAGRAF_14",
                "SU_PARAGRAF_15",
                "SU_PARAGRAF_16",
                "SU_PARAGRAF_19",
                "SU_PARAGRAF_20",
                "SU_PARAGRAF_22",
                "SU_PARAGRAF_23",
                "SU_PARAGRAF_24",
                "SU_PARAGRAF_25",
                "SU_PARAGRAF_26",
                "SU_PARAGRAF_27",
                "SU_PARAGRAF_28",
                "SU_PARAGRAF_29",
            ),
            klagenotat = null,
        ).toDomain().shouldBeLeft()
    }
}
