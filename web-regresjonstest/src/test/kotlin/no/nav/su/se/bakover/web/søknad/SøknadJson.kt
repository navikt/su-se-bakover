package no.nav.su.se.bakover.web.søknad

import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.web.SharedRegressionTestData

/**
 * @param lukket defaulter til ikke lukket (null)
 */
fun digitalUføreSøknadJson(
    id: String = "ignored-by-some-matchers",
    sakId: String = "ignored-by-some-matchers",
    fnr: String = SharedRegressionTestData.fnr,
    lukket: String? = null,
): String {
    return søknadJson(
        id = id,
        sakId = sakId,
        søknadInnhold = digitalSøknadsinnholdUføreJson(fnr),
        lukket = lukket,
    )
}

/**
 * @param lukket defaulter til ikke lukket (null)
 */
fun papirsøknadJson(
    id: String = "ignored-by-some-matchers",
    sakId: String = "ignored-by-some-matchers",
    fnr: String = SharedRegressionTestData.fnr,
    lukket: String? = null,
    mottaksdato: String = fixedLocalDate.toString(),
): String {
    return søknadJson(
        id = id,
        sakId = sakId,
        søknadInnhold = papirsøknadsinnholdUføreJson(fnr, mottaksdato),
        lukket = lukket,
    )
}

/**
 * Mapper til  [no.nav.su.se.bakover.web.routes.søknad.SøknadJson]
 *
 * id og sakId settes statisk og må ignoreres av en matcher.
 */
private fun søknadJson(
    id: String,
    sakId: String,
    søknadInnhold: String,
    lukket: String?,
): String {
    //language=JSON
    return """
    {
        "id":"$id",
        "sakId":"$sakId",
        "søknadInnhold": $søknadInnhold,
        "opprettet":"2021-01-01T01:02:03.456789Z",
        "lukket":${if (lukket != null) "$lukket" else null}
    }
    """.trimIndent()
}
