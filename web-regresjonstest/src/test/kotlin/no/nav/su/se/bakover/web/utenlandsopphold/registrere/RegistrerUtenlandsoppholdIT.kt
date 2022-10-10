package no.nav.su.se.bakover.web.utenlandsopphold.registrere

import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknadOgVerifiser
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class RegistrerUtenlandsoppholdIT {

    @Test
    fun `kan registere, oppdatere og ugyldiggjøre utenlandsopphold`() {
        val fnr = SharedRegressionTestData.fnr

        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            val nySøknadResponseJson = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                expectedSaksnummerInResponse = 2021, // Første saksnummer er alltid 2021 i en ny-migrert database.
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadResponseJson)
            val nyttUtenlandsopphold = this.nyttUtenlandsopphold(sakId)
            val utenlandsoppholdId =
                JSONObject(nyttUtenlandsopphold).getJSONArray("utenlandsopphold").getJSONObject(0).get("id").toString()
            val expected = """
            {
              "utenlandsopphold":[
                {
                  "id":"$utenlandsoppholdId",
                  "periode":{
                    "fraOgMed":"2021-05-05",
                    "tilOgMed":"2021-10-10"
                  },
                  "journalposter":[
                    "1234567"
                  ],
                  "dokumentasjon":"Sannsynliggjort",
                  "opprettetAv":"Z990Lokal",
                  "opprettetTidspunkt":"2021-01-01T01:02:03.456789Z",
                  "endretAv":"Z990Lokal",
                  "endretTidspunkt":"2021-01-01T01:02:03.456789Z",
                  "versjon":1,
                  "antallDager":157,
                  "erGyldig":true
                }
              ],
              "antallDager":157
            }
            """.trimIndent()

            JSONAssert.assertEquals(expected, nyttUtenlandsopphold, true)

            JSONAssert.assertEquals(
                """
                {
                "periode":{
                  "fraOgMed": "2021-05-05",
                  "tilOgMed": "2021-10-10"
                },
                "journalposter": ["1234567"],
                "dokumentasjon": "Sannsynliggjort"
              }
                """.trimIndent(),
                this.oppdaterUtenlandsopphold(sakId, utenlandsoppholdId),
                true,
            )
            JSONAssert.assertEquals(
                """{"utenlandsoppholdId":"$utenlandsoppholdId"}""",
                this.ugyldiggjørUtenlandsopphold(sakId, utenlandsoppholdId),
                true,
            )
        }
    }
}
