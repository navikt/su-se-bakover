package no.nav.su.se.bakover.web.routes.sak

import arrow.core.right
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.AlleredeGjeldendeSakForBruker
import no.nav.su.se.bakover.domain.BegrensetSakinfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

internal class SakRoutesKtTest {
    private val sakFnr01 = "12345678911"

    /**
     * Test for [ApplicationCall.lagCommandForLagreOgSendOpplastetPdfPåSak]
     */
    @Nested
    inner class multipartTests {
        @Test
        fun `Skal håndtere multiform med pdf og påkrevde felter fra pdf opplasting`() {
            val mockedSakService = mock<SakService> {}
            val journalpostTittel = "tittel"
            val distribusjonstype = Distribusjonstype.VEDTAK.name
            val pdfByteArray = ByteArray(313)
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            sakService = mockedSakService,
                        ),
                    )
                }
                defaultRequest(HttpMethod.Post, "$SAK_PATH/${UUID.randomUUID()}/fritekstDokument/lagreOgSend", listOf(Brukerrolle.Saksbehandler)) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("distribusjonstype", distribusjonstype)
                                append("tittel", journalpostTittel)
                                append(
                                    "pdf",
                                    pdfByteArray,
                                    Headers.build {
                                        append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                                        append(HttpHeaders.ContentDisposition, "filename=file.pdf")
                                    },
                                )
                            },
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.Accepted
                }
            }
            verify(mockedSakService, times(1)).lagreOgSendOpplastetPdfPåSak(
                request = argThat {
                    it.journaltittel shouldBe journalpostTittel
                    it.distribusjonstype.name shouldBe distribusjonstype
                    it.pdf.getContent() shouldBe pdfByteArray
                },
            )
        }

        @Test
        fun `Skal kaste ved tom pdf i multiform`() {
            val mockedSakService = mock<SakService> {}
            val journalpostTittel = "tittel"
            val distribusjonstype = Distribusjonstype.VEDTAK.name
            val pdfByteArray = ByteArray(0)
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            sakService = mockedSakService,
                        ),
                    )
                }
                defaultRequest(HttpMethod.Post, "$SAK_PATH/${UUID.randomUUID()}/fritekstDokument/lagreOgSend", listOf(Brukerrolle.Saksbehandler)) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("distribusjonstype", distribusjonstype)
                                append("tittel", journalpostTittel)
                                append(
                                    "pdf",
                                    pdfByteArray,
                                    Headers.build {
                                        append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                                        append(HttpHeaders.ContentDisposition, "filename=file.pdf")
                                    },
                                )
                            },
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.InternalServerError
                }
            }
            verify(mockedSakService, times(0)).lagreOgSendOpplastetPdfPåSak(
                any(),
            )
        }

        @Test
        fun `Skal kaste hvis multiform har feil name for form item`() {
            val mockedSakService = mock<SakService> {}
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            sakService = mockedSakService,
                        ),
                    )
                }
                defaultRequest(HttpMethod.Post, "$SAK_PATH/${UUID.randomUUID()}/fritekstDokument/lagreOgSend", listOf(Brukerrolle.Saksbehandler)) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("tullename", "tull")
                            },
                        ),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.InternalServerError
                }
            }
            verify(mockedSakService, times(0)).lagreOgSendOpplastetPdfPåSak(
                any(),
            )
        }
    }

    @Test
    fun `søk etter sak for fødselsnummer`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        sakService = mock {
                            on { hentSaker(any<Fnr>()) } doReturn listOf(
                                nySakUføre(
                                    sakInfo = SakInfo(
                                        sakId = sakId,
                                        saksnummer = saksnummer,
                                        fnr = Fnr(sakFnr01),
                                        type = Sakstype.UFØRE,
                                    ),
                                ).first,
                            ).right()
                        },
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, "$SAK_PATH/søk/fnr", listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"fnr":"$sakFnr01" }""")
            }.apply {
                status shouldBe OK
                bodyAsText() shouldContain """"fnr":"$sakFnr01""""
            }
        }
    }

    @Test
    fun `henter sak for fødselsnummer`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        sakService = mock {
                            on { hentSak(any<Fnr>(), any()) } doReturn nySakUføre(
                                sakInfo = SakInfo(
                                    sakId = sakId,
                                    saksnummer = saksnummer,
                                    fnr = Fnr(sakFnr01),
                                    type = Sakstype.UFØRE,
                                ),
                            ).first.right()
                        },
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, "$SAK_PATH/søk", listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"fnr":"$sakFnr01", "type": "uføre"}""")
            }.apply {
                status shouldBe OK
                bodyAsText() shouldContain """"fnr":"$sakFnr01""""
            }
        }
    }

    @Nested
    inner class BegrensetSakinfo {
        @Test
        fun `gir korrekt data når person ikke har søknad`() {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            sakService = mock {
                                on { hentAlleredeGjeldendeSakForBruker(any()) } doReturn AlleredeGjeldendeSakForBruker(
                                    uføre = BegrensetSakinfo(
                                        harÅpenSøknad = false,
                                        iverksattInnvilgetStønadsperiode = null,
                                    ),
                                    alder = BegrensetSakinfo(
                                        harÅpenSøknad = false,
                                        iverksattInnvilgetStønadsperiode = null,
                                    ),
                                )
                            },
                            person = mock {
                                on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
                            },
                        ),
                    )
                }
                defaultRequest(
                    Get,
                    "$SAK_PATH/info/$sakFnr01",
                    listOf(Brukerrolle.Veileder),
                ).apply {
                    status shouldBe OK
                    bodyAsText() shouldEqualJson """
                        {
                            "uføre": {
                                "harÅpenSøknad": false,
                                "iverksattInnvilgetStønadsperiode": null
                            },
                            "alder": {
                                "harÅpenSøknad": false,
                                "iverksattInnvilgetStønadsperiode": null
                            }
                        }
                    """.trimIndent()
                }
            }
        }

        @Test
        fun `finner ut om bruker har åpen søknad`() {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            sakService = mock {
                                on { hentAlleredeGjeldendeSakForBruker(any()) } doReturn AlleredeGjeldendeSakForBruker(
                                    uføre = BegrensetSakinfo(
                                        harÅpenSøknad = true,
                                        iverksattInnvilgetStønadsperiode = null,
                                    ),
                                    alder = BegrensetSakinfo(
                                        harÅpenSøknad = false,
                                        iverksattInnvilgetStønadsperiode = null,
                                    ),
                                )
                            },
                            person = mock {
                                on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
                            },
                        ),
                    )
                }
                defaultRequest(
                    Get,
                    "$SAK_PATH/info/$sakFnr01",
                    listOf(Brukerrolle.Veileder),
                ).apply {
                    status shouldBe OK
                    bodyAsText() shouldEqualJson """
                            {
                                "uføre": {
                                    "harÅpenSøknad": true,
                                    "iverksattInnvilgetStønadsperiode": null
                                },
                                "alder": {
                                    "harÅpenSøknad": false,
                                    "iverksattInnvilgetStønadsperiode": null
                                }
                            }
                    """.trimIndent()
                }
            }
        }

        @Test
        fun `finner ut om bruker har iverksatt innvilget stønadsperiode`() {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            sakService = mock {
                                on { hentAlleredeGjeldendeSakForBruker(any()) } doReturn AlleredeGjeldendeSakForBruker(
                                    uføre = BegrensetSakinfo(
                                        harÅpenSøknad = false,
                                        iverksattInnvilgetStønadsperiode = Periode.create(
                                            fraOgMed = stønadsperiode2021.periode.fraOgMed,
                                            tilOgMed = stønadsperiode2021.periode.tilOgMed,
                                        ),
                                    ),
                                    alder = BegrensetSakinfo(
                                        harÅpenSøknad = false,
                                        iverksattInnvilgetStønadsperiode = null,
                                    ),
                                )
                            },
                            person = mock {
                                on { sjekkTilgangTilPerson(any()) } doReturn Unit.right()
                            },
                        ),
                    )
                }
                defaultRequest(
                    Get,
                    "$SAK_PATH/info/$sakFnr01",
                    listOf(Brukerrolle.Veileder),
                ).apply {
                    status shouldBe OK
                    bodyAsText() shouldEqualJson """
                            {
                                "uføre": {
                                    "harÅpenSøknad": false,
                                    "iverksattInnvilgetStønadsperiode": {
                                        "fraOgMed": "${stønadsperiode2021.periode.fraOgMed}",
                                        "tilOgMed": "${stønadsperiode2021.periode.tilOgMed}"
                                    }
                                },
                                "alder": {
                                    "harÅpenSøknad": false,
                                    "iverksattInnvilgetStønadsperiode": null
                                }
                            }
                    """.trimIndent()
                }
            }
        }
    }

    @Test
    fun `error handling`() {
        testApplication {
            application { testSusebakoverWithMockedDb() }

            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/søk",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe BadRequest
            }

            defaultRequest(HttpMethod.Post, "$SAK_PATH/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"${Fnr.generer()}", type: "uføre"}""")
            }.apply {
                status shouldBe Forbidden
            }

            defaultRequest(HttpMethod.Post, "$SAK_PATH/søk", listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"saksnummer":"696969"}""")
            }.apply {
                status shouldBe NotFound
            }

            defaultRequest(HttpMethod.Post, "$SAK_PATH/søk", listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"saksnummer":"asdf"}""")
            }.apply {
                status shouldBe BadRequest
            }

            defaultRequest(
                Get,
                "$SAK_PATH/${UUID.randomUUID()}",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe NotFound
            }

            defaultRequest(
                Get,
                "$SAK_PATH/adad",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe BadRequest
            }
        }
    }
}
