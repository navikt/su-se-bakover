package no.nav.su.se.bakover.web.utenlandsopphold.registrere

import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknadOgVerifiser
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class RegistrerUtenlandsoppholdIT {
    @Test
    fun `kan registere nytt utenlandsopphold`() {
        val fnr = SharedRegressionTestData.fnr
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            val nySøknadResponseJson = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                expectedSaksnummerInResponse = 2021, // Første saksnummer er alltid 2021 i en ny-migrert database.
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadResponseJson)
            val expected = """
              {
                "periode":{
                  "fraOgMed": "2021-05-05",
                  "tilOgMed": "2021-10-10"
                },
                "journalpostIder": ["1234567"],
                "dokumentasjon": "Sannsynliggjort"
              }
            """.trimIndent()
            JSONAssert.assertEquals(expected, this.nyttUtenlandsopphold(sakId), true)
        }
    }
}
