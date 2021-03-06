package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.right
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.StønadsperiodeJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate

internal class StønadsperiodeJsonTest {

    private val fraOgMed = "2021-01-01"
    private val fraOgMedDato: LocalDate = LocalDate.of(2021, 1, 1)

    private val tilOgMed = "2021-12-31"
    private val tilOgMedDato: LocalDate = LocalDate.of(2021, 12, 31)

    private val json = """
        {
            "periode": {
                "fraOgMed":"$fraOgMed",
                "tilOgMed":"$tilOgMed"
            },
            "begrunnelse": ""
        }
    """.trimIndent()

    private val periode = Periode.create(fraOgMedDato, tilOgMedDato)
    private val stønadsperiode = Stønadsperiode.create(periode)

    @Test
    fun `kan serialisere`() {
        val actual = serialize(stønadsperiode.toJson())
        JSONAssert.assertEquals(json, actual, true)
    }

    @Test
    fun `kan deserialisere`() {
        val stønadsperiodeJson = deserialize<StønadsperiodeJson>(json)
        assertSoftly {
            stønadsperiodeJson shouldBe StønadsperiodeJson(
                periode = PeriodeJson(
                    fraOgMed = fraOgMed,
                    tilOgMed = tilOgMed,
                ),
                begrunnelse = "",
            )
            stønadsperiodeJson.toStønadsperiode() shouldBe stønadsperiode.right()
        }
    }
}
