package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import person.domain.PersonService
import vilkår.skatt.application.FrioppslagSkattRequest
import vilkår.skatt.application.SkatteService
import java.time.Year

class SkattRouteTest {

    @Test
    fun `ugyldige fødselsnumre avvises før skatteoppslag selv om det andre er gyldig`() {
        val skatteService = mock<SkatteService>()
        val ugyldigFnr = "ugyldig"
        val gyldigFnr = fnr.toString()
        val body = FrioppslagRequestBody(
            fnr = gyldigFnr,
            epsFnr = null,
            år = 2020,
            begrunnelse = "",
            sakstype = Sakstype.ALDER.value,
            fagsystemId = "",
        )
        val ugyldigeBodyer = listOf(
            body.copy(fnr = ugyldigFnr),
            body.copy(fnr = null, epsFnr = ugyldigFnr),
            body.copy(fnr = ugyldigFnr, epsFnr = gyldigFnr),
            body.copy(epsFnr = ugyldigFnr),
        )
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(skatteService = skatteService))
            }
            listOf("$SKATTE_PATH/forhandsvis", SKATTE_PATH, "$SKATTE_PATH/uten-verifisering").forEach { path ->
                ugyldigeBodyer.forEach { ugyldigBody ->
                    defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Saksbehandler)) {
                        setBody(serialize(ugyldigBody))
                    }.apply {
                        status shouldBe HttpStatusCode.BadRequest
                        deserialize<ErrorJson>(bodyAsText()) shouldBe ErrorJson("Ugyldig fødselsnummer", "ugyldig_fødselsnummer")
                    }
                }
            }
        }
        verifyNoInteractions(skatteService)
    }

    @Test
    fun `ugyldig sakstype avvises før skatteoppslag`() {
        val skatteService = mock<SkatteService>()
        val body = FrioppslagRequestBody(
            fnr = fnr.toString(),
            epsFnr = null,
            år = 2020,
            begrunnelse = "",
            sakstype = "ukjent",
            fagsystemId = "",
        )
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(skatteService = skatteService))
            }
            listOf("$SKATTE_PATH/forhandsvis", SKATTE_PATH, "$SKATTE_PATH/uten-verifisering").forEach { path ->
                defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Saksbehandler)) {
                    setBody(serialize(body))
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<ErrorJson>(bodyAsText()) shouldBe ErrorJson("Ugyldig sakstype", "ugyldig_sakstype")
                }
            }
        }
        verifyNoInteractions(skatteService)
    }

    @Test
    fun `veileder & drift har ikke tilgang til noe`() {
        listOf(Brukerrolle.Drift, Brukerrolle.Veileder).forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "/skatt/forhandsvis",
                    listOf(it),
                ).apply { status shouldBe HttpStatusCode.Forbidden }

                defaultRequest(
                    HttpMethod.Post,
                    "/skatt",
                    listOf(it),
                ).apply { status shouldBe HttpStatusCode.Forbidden }
            }
        }
    }

    @Test
    fun `lager forhåndsvisning av skatte-pdf`() {
        val pdfAsBytes = PdfA("<myPreciousByteArray.org".toByteArray())
        val skatteService = mock<SkatteService> {
            on { hentOgLagSkattePdf(any()) } doReturn pdfAsBytes.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }

        listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant).forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            skatteService = skatteService,
                            person = personService,
                        ),
                    )
                }
                defaultRequest(
                    HttpMethod.Post,
                    "/skatt/forhandsvis",
                    listOf(it),
                ) {
                    setBody(
                        serialize(
                            FrioppslagRequestBody(
                                fnr = fnr.toString(),
                                epsFnr = null,
                                år = 2020,
                                sakstype = Sakstype.ALDER.value,
                                fagsystemId = "",
                                begrunnelse = "Jeg vil snoke inn på naboen sin skattemelding",
                            ),
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    readRawBytes() shouldBe pdfAsBytes.getContent()
                    this.contentType() shouldBe ContentType.Application.Pdf
                }
            }
        }

        verify(skatteService, times(2)).hentOgLagSkattePdf(
            argThat {
                it shouldBe FrioppslagSkattRequest(
                    fnr = fnr,
                    epsFnr = null,
                    år = Year.of(2020),
                    begrunnelse = "Jeg vil snoke inn på naboen sin skattemelding",
                    saksbehandler = NavIdentBruker.Saksbehandler(DEFAULT_IDENT),
                    sakstype = Sakstype.ALDER,
                    fagsystemId = "",
                    verifiserAlder = true,
                )
            },
        )
    }

    @Test
    fun `avviser ugyldig begrunnelse ved forhåndsvisning`() {
        val skatteService = mock<SkatteService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        skatteService = skatteService,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "/skatt/forhandsvis",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    serialize(
                        FrioppslagRequestBody(
                            fnr = fnr.toString(),
                            epsFnr = null,
                            år = 2020,
                            sakstype = Sakstype.ALDER.value,
                            fagsystemId = "",
                            begrunnelse = "<script>alert(1)</script>",
                        ),
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldBe """{"message":"inneholder tegn utenfor tillatt tegnsett: '<>'","code":"ugyldig_fritekst_input"}"""
                contentType() shouldBe ContentType.Application.Json
            }
        }

        verify(skatteService, never()).hentOgLagSkattePdf(any())
    }

    @Test
    fun `godtar tom begrunnelse ved forhåndsvisning`() {
        val pdfAsBytes = PdfA("<myPreciousByteArray.org".toByteArray())
        val tomBegrunnelse = ""
        val skatteService = mock<SkatteService> {
            on { hentOgLagSkattePdf(any()) } doReturn pdfAsBytes.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        skatteService = skatteService,
                        person = personService,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "/skatt/forhandsvis",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    serialize(
                        FrioppslagRequestBody(
                            fnr = fnr.toString(),
                            epsFnr = null,
                            år = 2020,
                            sakstype = Sakstype.ALDER.value,
                            fagsystemId = "",
                            begrunnelse = tomBegrunnelse,
                        ),
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                readRawBytes() shouldBe pdfAsBytes.getContent()
                contentType() shouldBe ContentType.Application.Pdf
            }
        }

        verify(
            skatteService,
        ).hentOgLagSkattePdf(
            argThat {
                it.begrunnelse shouldBe tomBegrunnelse
            },
        )
    }

    @Test
    fun `godtar begrunnelse med 2000 tegn ved forhåndsvisning`() {
        val begrunnelse = "a".repeat(2000)
        val pdfAsBytes = PdfA("skatte-pdf".toByteArray())
        val skatteService = mock<SkatteService> {
            on { hentOgLagSkattePdf(any()) } doReturn pdfAsBytes.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        skatteService = skatteService,
                        person = personService,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "/skatt/forhandsvis",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    serialize(
                        FrioppslagRequestBody(
                            fnr = fnr.toString(),
                            epsFnr = null,
                            år = 2020,
                            sakstype = Sakstype.ALDER.value,
                            fagsystemId = "",
                            begrunnelse = begrunnelse,
                        ),
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                readRawBytes() shouldBe pdfAsBytes.getContent()
                contentType() shouldBe ContentType.Application.Pdf
            }
        }

        verify(skatteService).hentOgLagSkattePdf(
            argThat {
                it.begrunnelse shouldBe begrunnelse
            },
        )
    }

    @Test
    fun `avviser begrunnelse med 2001 tegn ved forhåndsvisning`() {
        val begrunnelse = "a".repeat(2001)
        val skatteService = mock<SkatteService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        skatteService = skatteService,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "/skatt/forhandsvis",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    serialize(
                        FrioppslagRequestBody(
                            fnr = fnr.toString(),
                            epsFnr = null,
                            år = 2020,
                            sakstype = Sakstype.ALDER.value,
                            fagsystemId = "",
                            begrunnelse = begrunnelse,
                        ),
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldBe """{"message":"for lang verdi, antall tegn 2001, maks 2000","code":"ugyldig_fritekst_input"}"""
                contentType() shouldBe ContentType.Application.Json
            }
        }

        verify(skatteService, never()).hentOgLagSkattePdf(any())
    }

    @Test
    fun `lager forhåndsvisning & journalfører av skatte-pdf`() {
        val pdfAsBytes = PdfA("<myPreciousByteArray.org".toByteArray())
        val skatteService = mock<SkatteService> {
            on { hentLagOgJournalførSkattePdf(any()) } doReturn pdfAsBytes.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }

        listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant).forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            skatteService = skatteService,
                            person = personService,
                        ),
                    )
                }
                defaultRequest(
                    HttpMethod.Post,
                    "/skatt",
                    listOf(it),
                ) {
                    setBody(
                        serialize(
                            FrioppslagRequestBody(
                                fnr = fnr.toString(),
                                epsFnr = null,
                                år = 2020,
                                sakstype = Sakstype.ALDER.value,
                                fagsystemId = "",
                                begrunnelse = "Jeg vil snoke inn på naboen sin skattemelding",
                            ),
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    readRawBytes() shouldBe pdfAsBytes.getContent()
                    this.contentType() shouldBe ContentType.Application.Pdf
                }
            }
        }

        verify(skatteService, times(2)).hentLagOgJournalførSkattePdf(
            argThat {
                it shouldBe FrioppslagSkattRequest(
                    fnr = fnr,
                    epsFnr = null,
                    år = Year.of(2020),
                    begrunnelse = "Jeg vil snoke inn på naboen sin skattemelding",
                    saksbehandler = NavIdentBruker.Saksbehandler(DEFAULT_IDENT),
                    sakstype = Sakstype.ALDER,
                    fagsystemId = "",
                    verifiserAlder = true,
                )
            },
        )
    }

    @Test
    fun `krever at det sendes inn søkers fnr, eps fnr, eller begge`() {
        listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant).forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(),
                    )
                }
                defaultRequest(HttpMethod.Post, "/skatt", listOf(it)) {
                    setBody(
                        serialize(
                            FrioppslagRequestBody(
                                fnr = null,
                                epsFnr = null,
                                år = 2020,
                                sakstype = Sakstype.ALDER.value,
                                fagsystemId = "",
                                begrunnelse = "Jeg vil snoke inn på naboen sin skattemelding",
                            ),
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    this.bodyAsText() shouldBe """{"message":"Krever at fnr eller epsFnr sendes inn. Eventuelt begge","code":"krever_at_minst_et_fnr_sendes_inn"}"""
                    this.contentType() shouldBe ContentType.Application.Json
                }
            }
        }
    }
}
