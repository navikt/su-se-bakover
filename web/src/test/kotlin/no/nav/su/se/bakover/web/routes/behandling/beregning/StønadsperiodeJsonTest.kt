package no.nav.su.se.bakover.web.routes.behandling.beregning

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.Stønadsperiode
import no.nav.su.se.bakover.web.routes.behandling.beregning.StønadsperiodeJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate

internal class StønadsperiodeJsonTest {

    private val fraOgMedDato: LocalDate = LocalDate.of(2021, 1, 1)
    private val tilOgMedDato: LocalDate = LocalDate.of(2021, 12, 31)

    private val json = """
            {
                "fraOgMed":"2021-01-01",
                "tilOgMed":"2021-12-31"
            }
        """.trimIndent()

    private val stønadsperiode = Stønadsperiode.create(Periode.create(fraOgMedDato, tilOgMedDato))

    @Test
    fun `kan serialisere`() {
        val actual = serialize(stønadsperiode.toJson())
        JSONAssert.assertEquals(json, actual, true)
    }

    @Test
    fun `kan deserialisere`() {
        deserialize<StønadsperiodeJson>(json).toStønadsperiode() shouldBe stønadsperiode.right()
    }
}
