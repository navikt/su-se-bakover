package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.kontrakt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.OppdaterStønadsperiodeRequest
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate

@Frontendkontrakt
internal class OppdaterStønadsperiodeRequestTest {

    private val fraOgMed = "2021-01-01"
    private val fraOgMedDato: LocalDate = 1.januar(2021)

    private val tilOgMed = "2021-12-31"
    private val tilOgMedDato: LocalDate = 31.desember(2021)

    //language=JSON
    private val requestJson = """
        {
            "periode": {
                "fraOgMed":"$fraOgMed",
                "tilOgMed":"$tilOgMed"
            },
            "harSaksbehandlerAvgjort": false
        }
    """.trimIndent()

    //language=JSON
    private val stønadsperiodeJson = """
        {
            "periode": {
                "fraOgMed":"$fraOgMed",
                "tilOgMed":"$tilOgMed"
            }
        }
    """.trimIndent()

    private val periode = Periode.create(fraOgMedDato, tilOgMedDato)
    private val stønadsperiode = Stønadsperiode.create(periode)

    @Test
    fun `kan serialisere`() {
        val actual = serialize(stønadsperiode.toJson())
        JSONAssert.assertEquals(stønadsperiodeJson, actual, true)
    }

    @Test
    fun `kan deserialisere`() {
        deserialize<OppdaterStønadsperiodeRequest>(requestJson) shouldBe OppdaterStønadsperiodeRequest(
            periode = PeriodeJson(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
            ),
            harSaksbehandlerAvgjort = false,
        )
    }
}
