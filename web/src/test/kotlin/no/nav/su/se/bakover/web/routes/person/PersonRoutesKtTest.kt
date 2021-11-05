package no.nav.su.se.bakover.web.routes.person

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class PersonRoutesKtTest {

    private val testIdent = "12345678910"
    private val person = PersonOppslagStub.nyTestPerson(Fnr(testIdent))

    private val services = TestServicesBuilder.services()

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            handleRequest(HttpMethod.Post, "$personPath/søk") {
                setBody("""{"fnr":"$testIdent"}""")
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `bad request ved ugyldig fnr`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(HttpMethod.Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"qwertyuiopå"}""")
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.BadRequest
            JSONAssert.assertEquals(
                """
                  {
                  "message": "Inneholder ikke et gyldig fødselsnummer",
                  "code": "ikke_gyldig_fødselsnummer"
                  }
                """.trimIndent(),
                response.content,
                true,
            )
        }
    }

    @Test
    fun `kan hente data gjennom PersonOppslag`() {
        val personServiceMock = mock<PersonService> { on { hentPerson(any()) } doReturn person.right() }
        val accessCheckProxyMock =
            mock<AccessCheckProxy> { on { proxy() } doReturn services.copy(person = personServiceMock) }

        //language=JSON
        val expectedResponseJson =
            """
                {
                    "fnr": "$testIdent",
                    "aktorId": "2437280977705",
                    "navn": {
                        "fornavn": "Tore",
                        "mellomnavn": "Johnas",
                        "etternavn": "Strømøy"
                    },
                    "telefonnummer": {
                        "landskode": "+47",
                        "nummer": "12345678"
                    },
                    "adresse": [{
                        "adresselinje": "Oslogata 12",
                        "postnummer": "0050",
                        "poststed": "OSLO",
                        "bruksenhet": "U1H20",
                        "kommunenavn": "OSLO",
                        "kommunenummer":"0301",
                        "adressetype": "Bostedsadresse",
                        "adresseformat": "Vegadresse"
                    }],
                    "sivilstand": {
                    "type": "GIFT",
                    "relatertVedSivilstand": "15116414950"
                    },
                    "statsborgerskap": "NOR",
                    "kjønn": "MANN",
                    "fødselsdato": "1990-01-01",
                    "alder": 31,
                    "adressebeskyttelse": null,
                    "skjermet": false,
                    "kontaktinfo": {
                        "epostadresse": "mail@epost.com",
                        "mobiltelefonnummer": "90909090",
                        "reservert": false,
                        "kanVarsles": true,
                        "språk": "nb"
                    },
                    "fullmakt": null,
                    "vergemål": null
                }
            """.trimIndent()

        withTestApplication(
            {
                testSusebakover(accessCheckProxy = accessCheckProxyMock, clock = fixedClock)
            },
        ) {
            defaultRequest(HttpMethod.Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"$testIdent"}""")
            }
        }.apply {
            response.status() shouldBe OK
            JSONAssert.assertEquals(expectedResponseJson, response.content!!, true)
        }
    }

    @Test
    fun `skal svare med 500 hvis ukjent feil`() {
        val clients = TestClientsBuilder.build(applicationConfig).copy(
            personOppslag = object : PersonOppslag {
                override fun person(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun personMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun aktørId(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun aktørIdMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")

                override fun sjekkTilgangTilPerson(fnr: Fnr) = KunneIkkeHentePerson.Ukjent.left()
            },
        )

        withTestApplication(
            {
                testSusebakover(clients = clients)
            },
        ) {
            defaultRequest(HttpMethod.Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"$testIdent"}""")
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.InternalServerError
            response.content
            JSONAssert.assertEquals(
                """
                  {
                  "message": "Feil ved oppslag på person",
                  "code": "feil_ved_oppslag_person"
                  }
                """.trimIndent(),
                response.content,
                true,
            )
        }
    }

    @Test
    fun `skal svare med 404 hvis person ikke funnet`() {
        val clients = TestClientsBuilder.build(applicationConfig).copy(
            personOppslag = object : PersonOppslag {
                override fun person(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun personMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun aktørId(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun aktørIdMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")

                override fun sjekkTilgangTilPerson(fnr: Fnr) = KunneIkkeHentePerson.FantIkkePerson.left()
            },
        )

        withTestApplication(
            {
                testSusebakover(clients = clients)
            },
        ) {
            defaultRequest(Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody(
                    """
                    {
                    "fnr": $testIdent
                    }
                    """.trimIndent(),
                )
            }
        }.apply {
            response.status() shouldBe NotFound
            JSONAssert.assertEquals(
                """
                  {
                  "message": "Fant ikke person",
                  "code": "fant_ikke_person"
                  }
                """.trimIndent(),
                response.content,
                true,
            )
        }
    }

    @Test
    fun `skal gi 403 når man ikke har tilgang til person`() {
        val clients = TestClientsBuilder.build(applicationConfig).copy(
            personOppslag = object : PersonOppslag {
                override fun person(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun personMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun aktørId(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                override fun aktørIdMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")

                override fun sjekkTilgangTilPerson(fnr: Fnr) = KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
            },
        )

        withTestApplication(
            {
                testSusebakover(
                    clients = clients,
                )
            },
        ) {
            defaultRequest(Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody(
                    """
                        {
                          "fnr": "${Fnr.generer()}"
                        }
                    """.trimIndent(),
                )
            }
        }.apply {
            response.status() shouldBe Forbidden
            response.content
            JSONAssert.assertEquals(
                """
                {
                  "message": "Ikke tilgang til å se person",
                  "code": "ikke_tilgang_til_person"
                }
                """.trimIndent(),
                response.content, true,
            )
        }
    }
}
