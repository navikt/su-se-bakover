package no.nav.su.se.bakover.web.sak

import no.nav.su.se.bakover.web.SharedRegressionTestData
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

/**
 * Hvis du overskriver en liste, må den wrappes i [].
 */
fun assertSakJson(
    actualSakJson: String,
    expectedId: String = "ignored-by-matcher",
    expectedSaksnummer: Long = 2021,
    expectedFnr: String = SharedRegressionTestData.fnr,
    expectedSøknader: String = "[]",
    expectedBehandlinger: String = "[]",
    expectedUtbetalinger: String = "[]",
    expectedUtbetalingerKanStansesEllerGjenopptas: String = "INGEN",
    expectedRevurderinger: String = "[]",
    expectedVedtak: String = "[]",
    expectedKlager: String = "[]"
) {
    // language=JSON
    val expectedSakJson = """
    {
        "id": "$expectedId",
        "saksnummer": $expectedSaksnummer,
        "fnr": "$expectedFnr",
        "søknader": $expectedSøknader,
        "behandlinger": $expectedBehandlinger,
        "utbetalinger": $expectedUtbetalinger,
        "utbetalingerKanStansesEllerGjenopptas": "$expectedUtbetalingerKanStansesEllerGjenopptas",
        "revurderinger": $expectedRevurderinger,
        "vedtak": $expectedVedtak,
        "klager": $expectedKlager
    }
    """.trimIndent()
    JSONAssert.assertEquals(
        actualSakJson,
        expectedSakJson,
        CustomComparator(
            JSONCompareMode.STRICT,
            Customization(
                "id",
            ) { _, _ -> true },
            Customization(
                "søknader[*].id",
            ) { _, _ -> true },
            Customization(
                "søknader[*].sakId",
            ) { _, _ -> true },
        ),
    )
}
