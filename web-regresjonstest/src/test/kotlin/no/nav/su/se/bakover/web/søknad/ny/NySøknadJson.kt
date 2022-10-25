package no.nav.su.se.bakover.web.søknad.ny

/**
 * Generelt ønsker vi ikke bruke implementasjonen når vi emulerer su-se-framover. Referanser til java.*, kotlin.* og *.test.* er ok.
 * Da vil vi plukke opp kontraktsendringer mellom bakover/fremover; både tilsiktede og utilsiktede.
 */
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.web.søknad.digitalSøknadsinnholdAlderJson
import no.nav.su.se.bakover.web.søknad.digitalSøknadsinnholdUføreJson
import no.nav.su.se.bakover.web.søknad.digitalUføreSøknadJson
import no.nav.su.se.bakover.web.søknad.papirsøknadJson
import no.nav.su.se.bakover.web.søknad.papirsøknadsinnholdUføreJson
import org.json.JSONObject

object NySøknadJson {
    /**
     * Henger tett sammen med [no.nav.su.se.bakover.web.søknad.ny.NySøknadJson.Response].
     * En forskjell er at requesten tar inn et rent [no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson] objekt,
     * mens responsen wrapper dette i [no.nav.su.se.bakover.web.routes.søknad.SøknadJson]
     */
    object Request {
        /**
         * Vil bruke søknadens opprettet når mottaksdato vurderes, i motsetning til forNav sin mottaksdato hvis det er en papirsøknad.
         */
        internal fun nyDigitalSøknad(
            fnr: String = no.nav.su.se.bakover.web.SharedRegressionTestData.fnr,
        ): String {
            return digitalSøknadsinnholdUføreJson(
                fnr = fnr,
            )
        }

        internal fun nyDigitalAlderssøknad(
            brukerFnr: String,
            epsFnr: String,
        ): String {
            return digitalSøknadsinnholdAlderJson(
                brukerFnr = brukerFnr,
                epsFnr = epsFnr,
            )
        }

        /**
         * Vil bruke mottaksdato i forNav-feltet i søknadsinnholdet, i motsetning til søknadens opprettet for digital søknader.
         */
        internal fun nyPapirsøknad(
            fnr: String = no.nav.su.se.bakover.web.SharedRegressionTestData.fnr,
            mottaksdato: String = fixedLocalDate.toString(),
        ): String {
            return papirsøknadsinnholdUføreJson(
                fnr = fnr,
                mottaksdato = mottaksdato,
            )
        }
    }

    /**
     * Henger tett sammen med [no.nav.su.se.bakover.web.søknad.ny.NySøknadJson.Request].
     * En forskjell er at requesten mapper til et [no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson] objekt,
     * mens responsen wrapper en [no.nav.su.se.bakover.web.routes.søknad.SøknadJson] og et saksnummer
     */
    object Response {

        /**
         * Henger tett sammen med [no.nav.su.se.bakover.web.søknad.ny.NySøknadJson.Request.nyDigitalSøknad]
         */
        fun nyDititalUføreSøknad(
            fnr: String = no.nav.su.se.bakover.web.SharedRegressionTestData.fnr,
            saksnummer: Long = 2021,
        ): String {
            val søknadJson = digitalUføreSøknadJson(fnr = fnr)
            //language=JSON
            return """
            {
              "saksnummer":$saksnummer,
              "søknad":$søknadJson
            }
            """.trimIndent()
        }

        /**
         * Henger tett sammen med [no.nav.su.se.bakover.web.søknad.ny.NySøknadJson.Request.nyPapirsøknad]
         */
        fun nyPapirsøknad(
            fnr: String = no.nav.su.se.bakover.web.SharedRegressionTestData.fnr,
            saksnummer: Long = 2021,
            mottaksdato: String = fixedLocalDate.toString(),
        ): String {
            val søknadJson = papirsøknadJson(
                fnr = fnr,
                mottaksdato = mottaksdato,
            )
            //language=JSON
            return """
              {
                "saksnummer":$saksnummer,
                "søknad": $søknadJson
              }
            """.trimIndent()
        }

        fun hentSakId(nySøknadResponseJson: String): String {
            return JSONObject(nySøknadResponseJson).getJSONObject("søknad").get("sakId").toString()
        }

        fun hentSøknadId(nySøknadResponseJson: String): String {
            return JSONObject(nySøknadResponseJson).getJSONObject("søknad").get("id").toString()
        }
    }
}
