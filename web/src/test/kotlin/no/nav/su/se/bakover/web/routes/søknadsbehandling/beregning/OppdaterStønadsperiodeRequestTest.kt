package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.right
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.OppdaterStønadsperiodeRequest.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate

internal class OppdaterStønadsperiodeRequestTest {

    private val fraOgMed = "2021-01-01"
    private val fraOgMedDato: LocalDate = 1.januar(2021)

    private val tilOgMed = "2021-12-31"
    private val tilOgMedDato: LocalDate = 31.desember(2021)

    private val requestJson = """
        {
            "periode": {
                "fraOgMed":"$fraOgMed",
                "tilOgMed":"$tilOgMed"
            },
            "harSaksbehandlerAvgjort": false
        }
    """.trimIndent()

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
        val request = deserialize<OppdaterStønadsperiodeRequest>(requestJson)
        assertSoftly {
            request shouldBe OppdaterStønadsperiodeRequest(
                periode = PeriodeJson(
                    fraOgMed = fraOgMed,
                    tilOgMed = tilOgMed,
                ),
                harSaksbehandlerAvgjort = false,
            )
            request.toDomain(fixedClock) shouldBe PartialOppdaterStønadsperiodeRequest(
                stønadsperiode = stønadsperiode,
                saksbehandlersAvgjørelse = null,
            ).right()
        }
    }
}
