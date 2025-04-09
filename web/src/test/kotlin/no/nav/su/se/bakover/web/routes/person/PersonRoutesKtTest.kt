package no.nav.su.se.bakover.web.routes.person

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import person.domain.KunneIkkeHentePerson
import person.domain.PersonOppslag
import person.domain.PersonService

internal class PersonRoutesKtTest {

    private val testIdent = "12345678910"
    private val person = PersonOppslagStub().nyTestPerson(Fnr(testIdent))

    private val services = TestServicesBuilder.services()

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }

            client.post("$PERSON_PATH/søk") {
                setBody("""{"fnr":"$testIdent"}""")
            }.apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `bad request ved ugyldig fnr`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(Post, "$PERSON_PATH/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"qwertyuiopå"}""")
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                JSONAssert.assertEquals(
                    """
                  {
                  "message": "Inneholder ikke et gyldig fødselsnummer",
                  "code": "ikke_gyldig_fødselsnummer"
                  }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test // TODO jah: Endre til integrasjonstest.
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
                    "sivilstand": null,
                    "statsborgerskap": "NOR",
                    "fødsel": {
                        "dato": "1990-01-01",
                        "år": 1990,
                        "alder": 31
                    },
                    "adressebeskyttelse": null,
                    "skjermet": false,
                    "kontaktinfo": {
                        "epostadresse": "mail@epost.com",
                        "mobiltelefonnummer": "90909090",
                        "språk": "nb",
                        "kanKontaktesDigitalt": true
                    },
                    "vergemål": null,
                    "dødsdato": null
                }
            """.trimIndent()

        testApplication {
            application {
                testSusebakoverWithMockedDb(clock = fixedClock, accessCheckProxy = accessCheckProxyMock)
            }
            defaultRequest(Post, "$PERSON_PATH/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"$testIdent"}""")
            }.apply {
                status shouldBe OK
                JSONAssert.assertEquals(expectedResponseJson, bodyAsText(), true)
            }
        }
    }

    @Test
    fun `skal svare med 500 hvis ukjent feil`() {
        val clients =
            TestClientsBuilder(fixedClock, mock { on { utbetaling } doReturn mock() }).build(applicationConfig()).copy(
                personOppslag = object : PersonOppslag {
                    override fun person(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                    override fun personMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                    override fun aktørIdMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")

                    override fun sjekkTilgangTilPerson(fnr: Fnr) = KunneIkkeHentePerson.Ukjent.left()
                },
            )

        testApplication {
            application {
                testSusebakoverWithMockedDb(clients = clients)
            }
            defaultRequest(Post, "$PERSON_PATH/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"$testIdent"}""")
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                JSONAssert.assertEquals(
                    """
                  {
                  "message": "Feil ved oppslag på person",
                  "code": "feil_ved_oppslag_person"
                  }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `skal svare med 404 hvis person ikke funnet`() {
        val clients =
            TestClientsBuilder(fixedClock, mock { on { utbetaling } doReturn mock() }).build(applicationConfig()).copy(
                personOppslag = object : PersonOppslag {
                    override fun person(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                    override fun personMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                    override fun aktørIdMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")

                    override fun sjekkTilgangTilPerson(fnr: Fnr) = KunneIkkeHentePerson.FantIkkePerson.left()
                },
            )

        testApplication {
            application {
                testSusebakoverWithMockedDb(clients = clients)
            }
            defaultRequest(Post, "$PERSON_PATH/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr": $testIdent}""")
            }.apply {
                this.status shouldBe NotFound
                JSONAssert.assertEquals(
                    """
                  {
                  "message": "Fant ikke person",
                  "code": "fant_ikke_person"
                  }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `skal gi 403 når man ikke har tilgang til person`() {
        val clients =
            TestClientsBuilder(fixedClock, mock { on { utbetaling } doReturn mock() }).build(applicationConfig()).copy(
                personOppslag = object : PersonOppslag {
                    override fun person(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                    override fun personMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")
                    override fun aktørIdMedSystembruker(fnr: Fnr) = throw RuntimeException("Skal ikke kalles på")

                    override fun sjekkTilgangTilPerson(fnr: Fnr) = KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
                },
            )

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clients = clients,
                )
            }
            defaultRequest(Post, "$PERSON_PATH/søk", listOf(Brukerrolle.Veileder)) {
                setBody(
                    """
                        {
                          "fnr": "${Fnr.generer()}"
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe Forbidden
                JSONAssert.assertEquals(
                    """
                {
                  "message": "Ikke tilgang til å se person",
                  "code": "ikke_tilgang_til_person"
                }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
