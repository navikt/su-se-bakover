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
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import person.domain.PersonService
import vilkår.skatt.application.FrioppslagSkattRequest
import vilkår.skatt.application.SkatteService
import java.time.Year

class SkattRouteTest {

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
            on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
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
                        //language=json
                        """
                        {
                          "fnr": "$fnr",
                          "epsFnr": null,
                          "år": 2020,
                          "sakstype": "alder",
                          "fagsystemId": "",
                          "begrunnelse": "Jeg vil snoke inn på naboen sin skattemelding"
                        }
                        """.trimIndent(),
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
    fun `lager forhåndsvisning & journalfører av skatte-pdf`() {
        val pdfAsBytes = PdfA("<myPreciousByteArray.org".toByteArray())
        val skatteService = mock<SkatteService> {
            on { hentLagOgJournalførSkattePdf(any()) } doReturn pdfAsBytes.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
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
                        //language=json
                        """
                        {
                          "fnr": "$fnr", 
                          "epsFnr": null,
                          "år": 2020,
                          "sakstype": "alder",
                          "fagsystemId": "",
                          "begrunnelse": "Jeg vil snoke inn på naboen sin skattemelding"
                        }
                        """.trimIndent(),
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
                        //language=json
                        """
                        {
                          "fnr": null, 
                          "epsFnr": null,
                          "år": 2020,
                          "sakstype": "alder",
                          "fagsystemId": "",
                          "begrunnelse": "Jeg vil snoke inn på naboen sin skattemelding"
                        }
                        """.trimIndent(),
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
