package no.nav.su.se.bakover.web.routes.kontrollsamtale

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup.KontrollsamtaleSetup
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.innkaltKontrollsamtale
import no.nav.su.se.bakover.test.planlagtKontrollsamtale
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtaleRoutesKtTest {

    private val validBody = """
         {"nyDato": "${LocalDate.now(fixedClock).plusMonths(2)}"}
    """.trimIndent()

    @Test
    fun `må være innlogget for å endre dato på kontrollsamtale`() {
        testApplication {
            application { testSusebakoverWithMockedDb() }
            client.post("/saker/${UUID.randomUUID()}/kontrollsamtaler/nyDato").apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `returnerer 200 ok dersom kallet treffer service`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on { nyDato(any(), any()) } doReturn Unit.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        kontrollsamtaleSetup = object : KontrollsamtaleSetup {
                            override val kontrollsamtaleService = kontrollsamtaleMock
                            override val opprettPlanlagtKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val annullerKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService
                                get() = fail("Should not end up here.")
                        },
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, "/saker/${UUID.randomUUID()}/kontrollsamtaler/nyDato", listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `må være innlogget for å hente kontrollsamtale`() {
        testApplication {
            application { testSusebakoverWithMockedDb() }
            client.get("/saker/${UUID.randomUUID()}/kontrollsamtaler/hent").apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `saksbehandler skal kunne hente neste planlagte kontrollsamtale`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on { hentNestePlanlagteKontrollsamtale(any(), anyOrNull()) } doReturn planlagtKontrollsamtale().right()
            on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        kontrollsamtaleSetup = object : KontrollsamtaleSetup {
                            override val kontrollsamtaleService = kontrollsamtaleMock
                            override val opprettPlanlagtKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val annullerKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val utløptFristForKontrollsamtaleService
                                get() = fail("Should not end up here.")
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "/saker/${UUID.randomUUID()}/kontrollsamtaler/hent",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `hent neste planlagte kontrollsamtale skal returnere 'null' om man ikke finner noen planlagte`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on {
                hentNestePlanlagteKontrollsamtale(
                    any(),
                    anyOrNull(),
                )
            } doReturn KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale.left()
            on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        kontrollsamtaleSetup = object : KontrollsamtaleSetup {
                            override val kontrollsamtaleService = kontrollsamtaleMock
                            override val opprettPlanlagtKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val annullerKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService
                                get() = fail("Should not end up here.")
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "/saker/${UUID.randomUUID()}/kontrollsamtaler/hent",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "fant_ikke_planlagt_kontrollsamtale"
            }
        }
    }

    @Test
    fun `hent neste planlagte kontrollsamtale skal feile ved andre feil`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on {
                hentNestePlanlagteKontrollsamtale(
                    any(),
                    anyOrNull(),
                )
            } doReturn KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        kontrollsamtaleSetup = object : KontrollsamtaleSetup {
                            override val kontrollsamtaleService = kontrollsamtaleMock
                            override val opprettPlanlagtKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val annullerKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService
                                get() = fail("Should not end up here.")
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "/saker/${UUID.randomUUID()}/kontrollsamtaler/hent",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    @Test
    fun `henter alle kontrollsamtaler på sak`() {
        val sakId = UUID.randomUUID()
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on {
                hentKontrollsamtaler(any())
            } doReturn listOf(innkaltKontrollsamtale(sakId = sakId), planlagtKontrollsamtale(sakId = sakId))
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        kontrollsamtaleSetup = object : KontrollsamtaleSetup {
                            override val kontrollsamtaleService = kontrollsamtaleMock
                            override val opprettPlanlagtKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val annullerKontrollsamtaleService
                                get() = fail("Should not end up here.")
                            override val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService
                                get() = fail("Should not end up here.")
                        },
                    ),
                )
            }
            defaultRequest(HttpMethod.Get, "/saker/$sakId/kontrollsamtaler", listOf(Brukerrolle.Saksbehandler))
                .apply {
                    status shouldBe HttpStatusCode.OK
                }
        }
    }
}
