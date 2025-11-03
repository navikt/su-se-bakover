package no.nav.su.se.bakover.web.søknad

import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
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

fun digitalAlderSøknadJson(
    id: String = "ignored-by-some-matchers",
    sakId: String = "ignored-by-some-matchers",
    fnr: String = SharedRegressionTestData.fnr,
    epsFnr: String = SharedRegressionTestData.epsFnr,
    lukket: String? = null,
): String {
    return søknadJson(
        id = id,
        sakId = sakId,
        søknadInnhold = digitalSøknadsinnholdAlderJson(fnr, epsFnr),
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
 * TODO: representerer [SøknadJson] -> web/src/main/kotlin/no/nav/su/se/bakover/web/routes/søknad/SøknadJson.kt
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
        "innsendtAv": "$DEFAULT_IDENT",
        "opprettet":"2021-01-01T01:02:03.456789Z",
        "lukket":${if (lukket != null) "$lukket" else null}
    }
    """.trimIndent()
}

fun søknadsbehandlingJson(
    søknad: String,
    sakstype: String = "uføre",
): String {
    //language=JSON
    return """
            {
              "id":"ignored",
              "søknad": $søknad,
              "beregning": null,
              "status": "OPPRETTET",
              "simulering": null,
              "opprettet": "2021-01-01T01:02:03.456789Z",
              "attesteringer": [],
              "saksbehandler": null,
              "fritekstTilBrev": "",
              "sakId": "5cb0d64c-9432-4d9f-840a-341c75ade20a",
              "stønadsperiode": null,
              "grunnlagsdataOgVilkårsvurderinger": {
                "uføre": null,
                "lovligOpphold": null,
                "fradrag": [],
                "bosituasjon": [],
                "formue": {
                  "vurderinger": [],
                  "resultat": null,
                  "formuegrenser": [
                    {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                    }
                  ]
                },
                "utenlandsopphold": null,
                "opplysningsplikt": null,
                "pensjon": null,
                "familiegjenforening": null,
                "flyktning": null,
                "fastOpphold": null,
                "personligOppmøte": null,
                "institusjonsopphold": null
              },
              "erLukket": false,
              "sakstype": "$sakstype",
              "aldersvurdering": null,
              "eksterneGrunnlag": {
                "skatt": null
              },
              "omgjøringsårsak": null,
              "omgjøringsgrunn": null
            }
    """.trimIndent()
}
