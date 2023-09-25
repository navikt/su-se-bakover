package no.nav.su.se.bakover.database.søknad

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.database.søknad.LukketJson.Companion.toLukketJson
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.avvistSøknadMedInformasjonsbrev
import no.nav.su.se.bakover.test.søknad.avvistSøknadMedVedtaksbrev
import no.nav.su.se.bakover.test.søknad.avvistSøknadUtenBrev
import no.nav.su.se.bakover.test.søknad.bortfaltSøknad
import no.nav.su.se.bakover.test.søknad.trukketSøknad
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LukketJsonTest {

    @Test
    fun bortfalt() {
        //language=JSON
        val expectedJson = """
            {
              "tidspunkt":"2021-01-01T01:02:04.456789Z",
              "saksbehandler":"saksbehandler",
              "type":"BORTFALT",
              "brevvalg":{
                "fritekst":null,
                "begrunnelse":"Saksbehandler får ikke per tidspunkt gjøre noen brevvalg dersom søknaden bortfaller.",
                "type":"SKAL_IKKE_SENDE_BREV"
              },
              "trukketDato":null
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            expectedJson,
            bortfaltSøknad().second.toLukketJson(),
            true,
        )
    }

    @Test
    fun trukket() {
        //language=JSON
        val expectedJson = """
            {
              "tidspunkt":"2021-01-01T01:02:04.456789Z",
              "saksbehandler":"saksbehandler",
              "type":"TRUKKET",
              "brevvalg":{
                "fritekst":null,
                "begrunnelse":"Saksbehandler får ikke per tidspunkt gjøre noen brevvalg dersom bruker trekker søknaden.",
                "type":"SKAL_SENDE_INFORMASJONSBREV_UTEN_FRITEKST"
              },
              "trukketDato":"2021-01-01"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            expectedJson,
            trukketSøknad().second.toLukketJson(),
            true,
        )
    }

    @Test
    fun `avvist uten brev`() {
        //language=JSON
        val expectedJson = """
            {
              "tidspunkt":"2021-01-01T01:02:04.456789Z",
              "saksbehandler":"saksbehandler",
              "type":"AVVIST",
              "brevvalg":{
                "fritekst":null,
                "begrunnelse":null,
                "type":"SAKSBEHANDLER_VALG_SKAL_IKKE_SENDE_BREV"
              },
              "trukketDato":null
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            expectedJson,
            avvistSøknadUtenBrev().second.toLukketJson(),
            true,
        )
    }

    @Test
    fun `avvist med informasjonsbrev`() {
        //language=JSON
        val expectedJson = """
         {
          "tidspunkt":"2021-01-01T01:02:03.456789Z",
          "saksbehandler":"saksbehandler",
          "type":"AVVIST",
          "brevvalg":{
            "fritekst":"Saksbehandler har lagt inn fritekst.",
            "begrunnelse":null,
            "type":"SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST"
          },
          "trukketDato":null
        }
        """.trimIndent()

        JSONAssert.assertEquals(
            expectedJson,
            avvistSøknadMedInformasjonsbrev(
                lukketTidspunkt = fixedTidspunkt,
                lukketAv = saksbehandler,
                brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("Saksbehandler har lagt inn fritekst."),
            ).toLukketJson(),
            true,
        )
    }

    @Test
    fun `avvist med vedtaksbrev`() {
        //language=JSON
        val expectedJson = """
        {
          "tidspunkt":"2021-01-01T01:02:03.456789Z",
          "saksbehandler":"saksbehandler",
          "type":"AVVIST",
          "brevvalg":{
            "fritekst":"skal sende med fritekst",
            "begrunnelse":null,
            "type":"SAKSBEHANDLER_VALG_SKAL_SENDE_VEDTAKSBREV_MED_FRITEKST"
          },
          "trukketDato":null
        }
        """.trimIndent()

        JSONAssert.assertEquals(
            expectedJson,
            avvistSøknadMedVedtaksbrev(
                lukketTidspunkt = fixedTidspunkt,
                lukketAv = saksbehandler,
                brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(null, "skal sende med fritekst"),
            ).toLukketJson(),
            true,
        )
    }
}
