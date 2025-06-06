package no.nav.su.se.bakover.web.routes.regulering

import io.kotest.assertions.json.shouldEqualJson
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.web.routes.regulering.json.toJson
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype

class ReguleringSomKreverManuellBehandlingJsonTest {

    @Test
    fun serialization() {
        val domeneobjekt = listOf(
            ReguleringSomKreverManuellBehandling(
                saksnummer = Saksnummer(2021),
                fnr = Fnr("10108000398"),
                reguleringId = ReguleringId.generer(),
                fradragsKategori = listOf(Fradragstype.Kategori.Fosterhjemsgodtgjørelse),
                årsakTilManuellRegulering = emptyList(),
            ),
        )

        domeneobjekt.toJson().also {
            it shouldEqualJson """
            [{
                "saksnummer": 2021,
                "fnr": "10108000398",
                "reguleringId": "${domeneobjekt.first().reguleringId}",
                "fradragsKategori": ["Fosterhjemsgodtgjørelse"]
            }]
            """.trimIndent()
        }
    }
}
