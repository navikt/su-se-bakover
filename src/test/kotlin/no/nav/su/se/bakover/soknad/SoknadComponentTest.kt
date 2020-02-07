package no.nav.su.se.bakover.soknad

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.google.gson.JsonParser
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.JwtStub
import no.nav.su.se.bakover.susebakover
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.withCallId
import org.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SoknadComponentTest {

    @Test
    fun `lagrer og henter søknad`() {
        val token = jwtStub.createTokenFor()
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            val lagreSøknadResponse = withCallId(Post, soknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                assertEquals(Created, response.status())
            }.response

            val søknadId = JSONObject(lagreSøknadResponse.content).getLong("søknadId")

            withCallId(Get, "$soknadPath/$søknadId") {
                addHeader(Authorization, "Bearer $token")
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(JsonParser().parse(soknadJson), JsonParser().parse(response.content)) // Må bruke JsonParser fordi json-elementene kan komme i forskjellig rekkefølge
            }
        }
    }

    @Test
    fun `lagrer og henter søknad på fnr`() {
        val token = jwtStub.createTokenFor()
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCallId(Post, soknadPath) {
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                assertEquals(Created, response.status())
            }

            withCallId(Get, "$soknadPath?$identLabel=$fnr") {
                addHeader(Authorization, "Bearer $token")
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(JsonParser().parse(soknadJson), JsonParser().parse(response.content))
            }
        }
    }

    companion object {
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        private val jwtStub by lazy {
            JwtStub(wireMockServer)
        }

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.start()
            WireMock.stubFor(jwtStub.stubbedJwkProvider())
            WireMock.stubFor(jwtStub.stubbedConfigProvider())
            WireMock.stubFor(jwtStub.stubbedTokenExchange())
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }

    val fnr = "234123345"
    private val soknadJson =
    """
    {
      "personopplysninger": {
        "fnr": $fnr,
        "fornavn": "fornavn",
        "mellomnavn": "ØÆÅ",
        "etternavn": "etternavn",
        "telefonnummer": "90011900",
        "gateadresse": "storgata 1",
        "bruksenhet": "20e",
        "postnummer": "0909",
        "poststed": "Oslo",
        "bokommune": "Oslo",
        "statsborgerskap": "Tunisisk",
        "flyktning": "true",
        "bofastnorge": "true"
      },
      "boforhold": {
        "borSammenMed": [
          "over18"
        ],
        "delerBoligMed": [
          {
            "navn": "Turid Schønberg",
            "fødselsnummer": "12312312312312"
          }
        ],
        "delerDuBolig": "true"
      },
      "utenlandsopphold": {
        "utenlandsoppholdArray": [
          {
            "utreisedato": "31122019",
            "innreisedato": "04012020"
          }
        ],
        "PlanlagtUtenlandsoppholdArray": [
          {
            "planlagtUtreisedato": "01032020",
            "planlagtInnreisedato": "05032020"
          },
          {
            "planlagtUtreisedato": "01042020",
            "planlagtInnreisedato": "05042020"
          }
        ],
        "utenlandsopphold": "true",
        "planlagtUtenlandsopphold": "true"
      },
      "oppholdstillatelse": {
        "varigopphold": "false",
        "oppholdstillatelseUtløpsdato": "30/10/2024",
        "soektforlengelse": "false"
      },
      "inntektPensjonFormue": {
        "pensjonsOrdning": [
          {
            "ordning": "KLP",
            "beløp": "99"
          },
          {
            "ordning": "SPK",
            "beløp": "98"
          }
        ],
        "kravannenytelse": "true",
        "kravannenytelseBegrunnelse": "Hundepensjon",
        "arbeidselleranneninntekt": "true",
        "arbeidselleranneninntektBegrunnelse": "2500",
        "hardupensjon": "true",
        "sumPersoninntekt": "30000",
        "harduformueeiendom": "true",
        "formueBeløp": "2323",
        "hardufinansformue": "false",
        "harduannenformueeiendom": "true",
        "typeFormue": "hytte i afrika",
        "samletSkattetakst": "3500",
        "sosialstonad": "true"
      },
      "forNAV": {
        "maalform": "nynorsk",
        "personligmote": "ja",
        "fullmektigmote": "ja",
        "passsjekk": "ja",
        "forNAVmerknader": "Trivelig type"
      }
    }
""".trimIndent()
}

