package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.test.beregning
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

// common-modulen har ikke tilgang til domain, så disse må ligger her en stund til.
internal class BeregningDatabaseMappingTest {

    @Test
    fun `krever at beregning er en instans eller null ved insert`() {
        assertThrows<IllegalArgumentException> {
            "update what ever u like where id=:id".insert(
                mapOf(
                    "beregning" to objectMapper.writeValueAsString(beregning()),
                ),
                mock(),
            )
        }

        assertDoesNotThrow {
            "update what ever u like where id=:id".insert(
                mapOf(
                    "beregning" to null,
                ),
                mock(),
            )
        }

        assertDoesNotThrow {
            "update what ever u like where id=:id".insert(
                mapOf(
                    "beregning" to beregning(),
                ),
                mock(),
            )
        }
    }

    @Test
    fun `krever at beregning er en instans eller null ved oppdatering`() {
        assertThrows<IllegalArgumentException> {
            "update what ever u like where id=:id".oppdatering(
                mapOf(
                    "beregning" to objectMapper.writeValueAsString(beregning()),
                ),
                mock(),
            )
        }

        assertDoesNotThrow {
            "update what ever u like where id=:id".oppdatering(
                mapOf(
                    "beregning" to null,
                ),
                mock(),
            )
        }

        assertDoesNotThrow {
            "update what ever u like where id=:id".oppdatering(
                mapOf(
                    "beregning" to beregning(),
                ),
                mock(),
            )
        }
    }
}
