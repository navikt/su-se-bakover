package no.nav.su.se.bakover.web.søknad

import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.web.SharedRegressionTestData

/**
 * @param lukket defaulter til ikke lukket (null)
 */
fun digitalSøknadJson(
    fnr: String = SharedRegressionTestData.fnr,
    lukket: String? = null,
): String {
    return søknadJson(
        søknadInnhold = digitalSøknadsinnholdJson(fnr),
        lukket = lukket,
    )
}

/**
 * @param lukket defaulter til ikke lukket (null)
 */
fun papirsøknadJson(
    fnr: String = SharedRegressionTestData.fnr,
    lukket: String? = null,
    mottaksdato: String = fixedLocalDate.toString(),
): String {
    return søknadJson(
        søknadInnhold = papirsøknadsinnholdJson(fnr, mottaksdato),
        lukket = lukket,
    )
}

/**
 * Mapper til  [no.nav.su.se.bakover.web.routes.søknad.SøknadJson]
 *
 * id og sakId settes statisk og må ignoreres av en matcher.
 */
private fun søknadJson(
    søknadInnhold: String,
    lukket: String?,
): String {
    //language=JSON
    return """
    {
        "id":"ignored-by-matcher",
        "sakId":"ignored-by-matcher",
        "søknadInnhold": $søknadInnhold,
        "opprettet":"2021-01-01T01:02:03.456789Z",
        "lukket":${if (lukket != null) "$lukket" else null}
    }
    """.trimIndent()
}
