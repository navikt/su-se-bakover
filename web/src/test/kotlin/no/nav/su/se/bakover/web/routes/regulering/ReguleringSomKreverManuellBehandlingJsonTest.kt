package no.nav.su.se.bakover.web.routes.regulering

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.ReguleringMerknad
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.sak.Saksnummer
import org.junit.jupiter.api.Test
import java.util.UUID

class ReguleringSomKreverManuellBehandlingJsonTest {

    @Test
    fun `serialization and deserialization test`() {
        val domeneobjekt = listOf(
            ReguleringSomKreverManuellBehandling(
                saksnummer = Saksnummer(2021),
                fnr = Fnr("10108000398"),
                reguleringId = UUID.randomUUID(),
                merknader = listOf(ReguleringMerknad.Fosterhjemsgodtgjørelse),
            ),
        )

        val serialisert = domeneobjekt.toJson().also {
            it shouldEqualJson """
            [{
                "saksnummer": 2021,
                "fnr": "10108000398",
                "reguleringId": "${domeneobjekt.first().reguleringId}",
                "merknader": ["Fosterhjemsgodtgjørelse"]
            }]
            """.trimIndent()
        }

        deserializeList<ReguleringSomKreverManuellBehandling>(serialisert) shouldBe domeneobjekt
    }
}
