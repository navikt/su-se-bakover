package no.nav.su.se.bakover.web.routes.drift

import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.formdataRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test

class InnlesingPersonhendelserFraFilRouteKtTest {

    private val services = TestServicesBuilder.services()

    private val escapedDateField = "\$date"

    //language=json
    val personhendelseJsonFileContent = """[
           {
              "_id":"ed418b70-dc7d-4033-bce3-bac16dad2ec2",
              "data":{
                 "hendelseId":"41535c17-f53c-406e-8888-a275bc3ea4cd",
                 "personidenter":["11111111111","22222222222","1313131313131"],
                 "master":"FREG",
                 "opprettet":1711119474304,
                 "opplysningstype":"SIVILSTAND_V1",
                 "endringstype":"OPPRETTET",
                 "tidligereHendelseId":null,
                 "adressebeskyttelse":null,
                 "doedfoedtBarn":null,
                 "doedsfall":null,
                 "foedsel":null,
                 "forelderBarnRelasjon":null,
                 "familierelasjon":null,
                 "sivilstand":null,
                 "vergemaalEllerFremtidsfullmakt":null,
                 "utflyttingFraNorge":null,
                 "InnflyttingTilNorge":null,
                 "Folkeregisteridentifikator":null,
                 "navn":null,
                 "sikkerhetstiltak":null,
                 "statsborgerskap":null,
                 "telefonnummer":null,
                 "kontaktadresse":null,
                 "bostedsadresse":null
              },
              "identer":["11111111111","22222222222","1313131313131"],
              "offset":12345,
              "partition":0,
              "timestamp":{"$escapedDateField":"2021-01-01T08:15:48.126Z"}
           },
                          {
              "_id":"0de6b9e2-01ac-49e2-a63f-5c013cde489c",
              "data":{
                 "hendelseId":"517a4474-0ce9-42f6-b7b1-65fec5a88cc8",
                 "personidenter":["1212121212121","33333333333"],
                 "master":"FREG",
                 "opprettet":12312313123123,
                 "opplysningstype":"STATSBORGERSKAP_V1",
                 "endringstype":"KORRIGERT",
                 "tidligereHendelseId":"497e4279-a257-4fc4-bfdf-b7fd7fa394c9",
                 "adressebeskyttelse":null,
                 "doedfoedtBarn":null,
                 "doedsfall":{"doedsdato":11111},
                 "foedsel":null,
                 "forelderBarnRelasjon":null,
                 "familierelasjon":null,
                 "sivilstand":{
                    "type":"GIFT",
                    "gyldigFraOgMed":12345,
                    "relatertVedSivilstand":null,
                    "bekreftelsesdato":null
                 },
                 "vergemaalEllerFremtidsfullmakt":null,
                 "utflyttingFraNorge":{
                    "tilflyttingsland":"ABCD",
                    "tilflyttingsstedIUtlandet":null,
                    "utflyttingsdato":1111
                 },
                 "InnflyttingTilNorge":null,
                 "Folkeregisteridentifikator":null,
                 "navn":null,
                 "sikkerhetstiltak":null,
                 "statsborgerskap":{
                    "land":"ABCDEF",
                    "gyldigFom":1000,
                    "gyldigTom":null,
                    "bekreftelsesdato":null
                 },
                 "telefonnummer":null,
                  "kontaktadresse":{
                    "gyldigFraOgMed":null,
                    "gyldigTilOgMed":null,
                    "type":"En type",
                    "coAdressenavn":"Et co-adressenavn",
                    "postboksadresse":null,
                    "vegadresse":null,
                    "postadresseIFrittFormat":{
                       "adresselinje1":"Første adresselinje",
                       "adresselinje2":"Andre adresselinje",
                       "adresselinje3":null,
                       "postnummer":"111111"
                    },
                    "utenlandskAdresse":null,
                    "utenlandskAdresseIFrittFormat":null
                 },
                 "bostedsadresse":{
                    "angittFlyttedato":2345,
                    "gyldigFraOgMed":null,
                    "gyldigTilOgMed":null,
                    "coAdressenavn":"",
                    "vegadresse":{
                       "matrikkelId":"11111111",
                       "husnummer":"999",
                       "husbokstav":null,
                       "bruksenhetsnummer":"H101001",
                       "adressenavn":"Goldshire",
                       "kommunenummer":"101",
                       "bydelsnummer":null,
                       "tilleggsnavn":null,
                       "postnummer":"11011",
                       "koordinater":{"x":1111,"y":111111,"z":0}
                    },
                    "matrikkeladresse":null,
                    "utenlandskAdresse":null,
                    "ukjentBosted":null
                 }
             },
              "identer":["1212121212121","33333333333"],
              "offset":1221172,
              "partition":2,
              "timestamp":{"$escapedDateField":"2022-02-05T12:16:15.356Z"}
           }
        ]
    """.trimIndent()

    @Test
    fun `leser og deserialiserer personhendelse fra fil`() {
        testApplication {
            application { testSusebakoverWithMockedDb(services = services) }
            formdataRequest(
                method = HttpMethod.Post,
                uri = "$DRIFT_PATH/personhendelser",
                roller = listOf(Brukerrolle.Drift),
                formData = formData {
                    append("image", "file.json", ContentType.Application.Json) {
                        this.append(personhendelseJsonFileContent)
                    }
                },
            ).apply {
                this.bodyAsText() shouldBe """{"status": "OK"}"""
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}
