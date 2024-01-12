package no.nav.su.se.bakover.web.routes.sak

import arrow.core.right
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
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
import java.util.UUID

internal class SakRoutesKtTest {
    private val sakFnr01 = "12345678911"

    @Test
    fun `henter sak for fødselsnummer`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        sak = mock {
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
                            sak = mock {
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
                            sak = mock {
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
                            sak = mock {
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
