package no.nav.su.se.bakover.service.klage

import behandling.klage.domain.Hjemmel
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import org.junit.jupiter.api.Test

internal class KlageVurderingerRequestTest {

    @Test
    fun `gyldige hjemler`() {
        val alleGyldigeHjemler = Hjemmel.entries.map { it.name }

        KlageVurderingerRequest.SkalTilKabal(
            hjemler = alleGyldigeHjemler,
            klagenotat = "klagenotat",
            erOppretthold = true,
        ).toDomain().shouldBeRight()
    }

    @Test
    fun `ugyldige hjemler`() {
        KlageVurderingerRequest.SkalTilKabal(
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
            erOppretthold = true,
        ).toDomain().shouldBeLeft()
    }
}
