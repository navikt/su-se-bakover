package no.nav.su.se.bakover.web.sak

import no.nav.su.se.bakover.test.jsonAssertEquals
import no.nav.su.se.bakover.web.SharedRegressionTestData

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
    expectedKlager: String = "[]",
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
    jsonAssertEquals(
        expected = expectedSakJson,
        actual = actualSakJson,
        "id",
        "søknader[*].id",
        "søknader[*].sakId",
        "utbetalinger[*].sakId",
        "vedtak[*].id",
        "vedtak[*].utbetalingId",
        "vedtak[*].sakId",
        "vedtak[*].behandlingId",
        "behandlinger[*].grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id", // TODO jah: Finn ut hvorfor denne forandrer seg.
        "behandlinger[*].grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].opprettet", // TODO jah: Finn ut hvorfor denne forandrer seg.
        "utbetalinger[*].fraOgMed", // TODO jah: Finn ut hvorfor denne ignorerer fixedClock.
        "utbetalinger[*].tilOgMed", // TODO jah: Finn ut hvorfor denne ignorerer fixedClock.
    )
}
