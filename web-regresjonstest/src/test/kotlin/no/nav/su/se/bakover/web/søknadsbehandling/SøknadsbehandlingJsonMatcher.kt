package no.nav.su.se.bakover.web.søknadsbehandling

import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

/**
 * Hvis du overskriver en liste, må den wrappes i [].
 */
fun assertSøknadsbehandlingJson(
    actualSøknadsbehandlingJson: String,
    expectedId: String = "ignored-by-matcher",
    expectedBehandlingsinformasjon: String,
    expectedSøknad: String,
    expectedBeregning: String? = null,
    expectedStatus: String = "OPPRETTET",
    expectedSimulering: String? = null,
    expectedOpprettet: String = "2021-01-01T01:02:03.456789Z",
    expectedAttesteringer: String = "[]",
    expectedSaksbehandler: String? = null,
    expectedFritekstTilBrev: String = "",
    expectedSakId: String,
    expectedStønadsperiode: String? = null, // """{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-01"},"begrunnelse":""}""",
    expectedGrunnlagsdataOgVilkårsvurderinger: String,
    expectedErLukket: Boolean = false,
    expectedSimuleringForAvkortingsvarsel: String? = null,
) {
    val expectedSakJson = """
    {
        "id": "$expectedId",
        "behandlingsinformasjon": $expectedBehandlingsinformasjon,
        "søknad": $expectedSøknad,
        "beregning": $expectedBeregning,
        "status": "$expectedStatus",
        "simulering": $expectedSimulering,
        "opprettet": "$expectedOpprettet",
        "attesteringer": $expectedAttesteringer,
        "saksbehandler": ${if (expectedSaksbehandler == null) null else "$expectedSaksbehandler"},
        "fritekstTilBrev": "$expectedFritekstTilBrev",
        "sakId": "$expectedSakId",
        "stønadsperiode": $expectedStønadsperiode,
        "grunnlagsdataOgVilkårsvurderinger": $expectedGrunnlagsdataOgVilkårsvurderinger,
        "erLukket": $expectedErLukket,
        "simuleringForAvkortingsvarsel": $expectedSimuleringForAvkortingsvarsel
    }
    """.trimIndent()
    JSONAssert.assertEquals(
        expectedSakJson,
        actualSøknadsbehandlingJson,
        CustomComparator(
            JSONCompareMode.STRICT,
            Customization(
                "id",
            ) { _, _ -> true },
        ),
    )
}
