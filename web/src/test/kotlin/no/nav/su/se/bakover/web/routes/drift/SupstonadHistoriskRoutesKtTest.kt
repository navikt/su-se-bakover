package no.nav.su.se.bakover.web.routes.drift

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.client.historisk.KolonnebeskrivelseDto
import no.nav.su.se.bakover.client.historisk.SchemaDto
import no.nav.su.se.bakover.client.historisk.UttrekkResponse
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.service.historisk.KunneIkkeKonvertereHistoriskeData
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class SupstonadHistoriskRoutesKtTest {

    private val uri = "$DRIFT_PATH/supstonadhistorisk/tellrader"
    private val requestBody = """{ "tabellnavn": "sak" }"""

    @Test
    fun `Kun Drift har tilgang til tellrader-endepunktet`() {
        Brukerrolle.entries.filterNot { it == Brukerrolle.Drift }.forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
                }
                defaultRequest(
                    method = HttpMethod.Post,
                    uri = uri,
                    roller = listOf(it),

                ) { setBody(requestBody) }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `tellrader gir OK med antall rader`() {
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { tellRader(any()) } doReturn CountResponse(antall = 42).right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = uri,
                roller = listOf(Brukerrolle.Drift),
            ) { setBody(requestBody) }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """{ "antall": 42 }""",
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `tellrader gir feilstatus og feilmelding når klienten feiler`() {
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { tellRader(any()) } doReturn ClientError(
                httpStatus = 502,
                message = "Klarte ikke å nå supstonad-historisk",
            ).left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = uri,
                roller = listOf(Brukerrolle.Drift),
            ) { setBody(requestBody) }.apply {
                status shouldBe HttpStatusCode.BadGateway
                JSONAssert.assertEquals(
                    """{ "message": "Klarte ikke å nå supstonad-historisk", "code": "supstonad_historisk_feil" }""",
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    private val hentUttrekkUri = "$DRIFT_PATH/supstonadhistorisk/hentuttrekk"
    private val hentUttrekkRequestBody = """{ "tabellnavn": "sak", "antallRader": 10 }"""

    @Test
    fun `Kun Drift har tilgang til hentuttrekk-endepunktet`() {
        Brukerrolle.entries.filterNot { it == Brukerrolle.Drift }.forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
                }
                defaultRequest(
                    method = HttpMethod.Post,
                    uri = hentUttrekkUri,
                    roller = listOf(it),
                ) { setBody(hentUttrekkRequestBody) }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `hentuttrekk gir OK med uttrekk`() {
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on {
                hentUttrekk(
                    tabellnavn = any(),
                    antallRader = any(),
                    iterator = anyOrNull(),
                )
            } doReturn UttrekkResponse(
                iterator = "nesteSide",
                schema = SchemaDto(kolonner = listOf(KolonnebeskrivelseDto(navn = "id"))),
                innhold = listOf(listOf("1", "2")),
            ).right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = hentUttrekkUri,
                roller = listOf(Brukerrolle.Drift),
            ) { setBody(hentUttrekkRequestBody) }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """
                    {
                        "iterator": "nesteSide",
                        "schema": { "kolonner": [ { "navn": "id" } ] },
                        "innhold": [ [ "1", "2" ] ]
                    }
                    """.trimIndent(),
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `hentuttrekk gir feilstatus og feilmelding når klienten feiler`() {
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on {
                hentUttrekk(
                    tabellnavn = any(),
                    antallRader = any(),
                    iterator = anyOrNull(),
                )
            } doReturn ClientError(
                httpStatus = 502,
                message = "Klarte ikke å nå supstonad-historisk",
            ).left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = hentUttrekkUri,
                roller = listOf(Brukerrolle.Drift),
            ) { setBody(hentUttrekkRequestBody) }.apply {
                status shouldBe HttpStatusCode.BadGateway
                JSONAssert.assertEquals(
                    """{ "message": "Klarte ikke å nå supstonad-historisk", "code": "supstonad_historisk_feil" }""",
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `hentuttrekk med antallRader mindre eller lik null gir BadRequest`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = hentUttrekkUri,
                roller = listOf(Brukerrolle.Drift),
            ) { setBody("""{ "tabellnavn": "sak", "antallRader": 0 }""") }.apply {
                status shouldBe HttpStatusCode.BadRequest
                JSONAssert.assertEquals(
                    """{ "message": "antallRader må være større enn 0", "code": "supstonad_historisk_ugyldig_antall_rader" }""",
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `konvertering startes asynkront og svarer accepted`() {
        val importId = UUID.randomUUID()
        val projeksjonId = UUID.randomUUID()
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { opprettAldersprojeksjon(importId, null) } doReturn projeksjonId.right()
            on { konverterAldersstønader(projeksjonId, importId, null) } doReturn
                KunneIkkeKonvertereHistoriskeData.UventetFeil("test").left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$DRIFT_PATH/supstonadhistorisk/import/$importId/konverter",
                roller = listOf(Brukerrolle.Drift),
            ).apply {
                status shouldBe HttpStatusCode.Accepted
                JSONAssert.assertEquals(
                    """{"projeksjonId":"$projeksjonId"}""",
                    bodyAsText(),
                    true,
                )
            }

            verify(supstonadHistoriskService).opprettAldersprojeksjon(importId, null)
            verify(supstonadHistoriskService, timeout(1_000))
                .konverterAldersstønader(projeksjonId, importId, null)
        }
    }

    @Test
    fun `dry-run sender maks antall stønader til den asynkrone konverteringen`() {
        val importId = UUID.randomUUID()
        val projeksjonId = UUID.randomUUID()
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { opprettAldersprojeksjon(importId, 25) } doReturn projeksjonId.right()
            on { konverterAldersstønader(projeksjonId, importId, 25) } doReturn
                KunneIkkeKonvertereHistoriskeData.UventetFeil("test").left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$DRIFT_PATH/supstonadhistorisk/import/$importId/konverter?maksAntallStonader=25",
                roller = listOf(Brukerrolle.Drift),
            ).apply {
                status shouldBe HttpStatusCode.Accepted
            }

            verify(supstonadHistoriskService).opprettAldersprojeksjon(importId, 25)
            verify(supstonadHistoriskService, timeout(1_000))
                .konverterAldersstønader(projeksjonId, importId, 25)
        }
    }

    @Test
    fun `henter konverteringsstatus for en import`() {
        val importId = UUID.randomUUID()
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { hentAldersprojeksjoner(importId) } doReturn emptyList()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }

            defaultRequest(
                method = HttpMethod.Get,
                uri = "$DRIFT_PATH/supstonadhistorisk/import/$importId/konverteringer",
                roller = listOf(Brukerrolle.Drift),
            ).apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe "[]"
            }
        }
    }

    @Test
    fun `dry-run avviser ugyldig maks antall stønader`() {
        val importId = UUID.randomUUID()
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$DRIFT_PATH/supstonadhistorisk/import/$importId/konverter?maksAntallStonader=0",
                roller = listOf(Brukerrolle.Drift),
            ).apply {
                status shouldBe HttpStatusCode.BadRequest
                JSONAssert.assertEquals(
                    """
                    {
                      "message": "maksAntallStonader må være et heltall større enn 0",
                      "code": "ugyldig_maks_antall_stonader"
                    }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
