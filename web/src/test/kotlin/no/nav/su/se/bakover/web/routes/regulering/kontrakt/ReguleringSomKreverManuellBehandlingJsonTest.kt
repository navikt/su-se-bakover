package no.nav.su.se.bakover.web.routes.regulering.kontrakt

import io.kotest.assertions.json.shouldEqualJson
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellReguleringKategori
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
                reguleringId = ReguleringId.fraString("5b3c1d0e-8f2a-4b6c-9d7e-1a2b3c4d5e6f"),
                fradragsKategori = listOf(Fradragstype.Kategori.Fosterhjemsgodtgjørelse),
                årsakTilManuellRegulering = listOf(ÅrsakTilManuellReguleringKategori.ManglerRegulertBeløpForFradrag),
                status = "OPPRETTET",
                sisteVedtakType = "SØKNAD",
                sisteVedtakOpprettet = fixedTidspunkt,
            ),
        )

        domeneobjekt.toJson().also {
            //language=JSON
            it shouldEqualJson """
            [{
                "saksnummer": 2021,
                "reguleringId": "5b3c1d0e-8f2a-4b6c-9d7e-1a2b3c4d5e6f",
                "fradragsKategori": ["Fosterhjemsgodtgjørelse"],
                "årsakTilManuellRegulering": ["ManglerRegulertBeløpForFradrag"],
                "status": "OPPRETTET",
                "sisteVedtakType": "SØKNAD",
                "sisteVedtakOpprettet": "2021-01-01T01:02:03.456789Z"
            }]
            """.trimIndent()
        }
    }
}
