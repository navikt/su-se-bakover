package no.nav.su.se.bakover.web.routes.regulering.kontrakt

import io.kotest.assertions.json.shouldEqualJson
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.regulering.json.toJson
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.Fradragstype

@Frontendkontrakt
class ReguleringSomKreverManuellBehandlingJsonTest {

    @Test
    fun serialization() {
        val domeneobjekt = listOf(
            ReguleringSomKreverManuellBehandling(
                saksnummer = Saksnummer(2021),
                reguleringId = ReguleringId.generer(),
                fradragsKategori = listOf(Fradragstype.Kategori.Fosterhjemsgodtgjørelse),
                årsakTilManuellRegulering = emptyList(),
                status = "OPPRETTET",
                sisteVedtakType = "SØKNAD",
                sisteVedtakOpprettet = fixedTidspunkt,
            ),
        )

        domeneobjekt.toJson().also {
            it shouldEqualJson """
            [{
                "saksnummer": 2021,               
                "reguleringId": "${domeneobjekt.first().reguleringId}",
                "fradragsKategori": ["Fosterhjemsgodtgjørelse"],
                "årsakTilManuellRegulering": [],
                "status": "OPPRETTET",
                "sisteVedtakType": "SØKNAD",
                "sisteVedtakOpprettet": "$fixedTidspunkt"
            }]
            """.trimIndent()
        }
    }
}
