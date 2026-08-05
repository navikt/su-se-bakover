package no.nav.su.se.bakover.web.routes.grunnlag.eksterneFradrag

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.mockedDb
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import person.domain.PersonService
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal class EksterneFradragRoutesTest {

    private val sakId = UUID.randomUUID()
    private val clock = fixedClock
    private val fnr1 = fnr
    private val dagensMåned = YearMonth.from(LocalDate.now(clock))
    private val gyldigPeriode = periode(
        fraOgMed = dagensMåned,
        tilOgMed = dagensMåned.plusMonths(5),
    )
    private val periodeMerEnnEttÅrTilbake = periode(
        fraOgMed = dagensMåned.minusYears(1).minusMonths(1),
        tilOgMed = dagensMåned.plusMonths(5),
    )

    @Test
    fun `hentFradragAlderspensjon returnerer 403 for veileder`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(clock = clock)
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/fradrag/eksternt/$sakId/alderspensjon",
                roller = listOf(Brukerrolle.Veileder),
            ) {
                setBody(serialize(request(gyldigPeriode)))
            }.apply {
                status shouldBe HttpStatusCode.Forbidden
            }
        }
    }

    @Test
    fun `hentFradragAlderspensjon returnerer 500 når pesysClient feiler`() {
        val pesysClient = mock<PesysClient> {
            on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ClientError(500, "Pesys error").left()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(sakId) } doReturn SakInfo(sakId, Saksnummer(124234L), fnr1, Sakstype.UFØRE).right()
        }
        val clients = TestClientsBuilder(clock, mockedDb(), pesysClient = pesysClient).build(applicationConfig())

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = clock,
                    clients = clients,
                    services = TestServicesBuilder.services(person = personService, sakService = sakService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/fradrag/eksternt/$sakId/alderspensjon",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request(gyldigPeriode)))
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldBe """{"message":"Kunne ikke hente data fra Pesys","code":"external_service_error"}"""
                contentType() shouldBe ContentType.Application.Json
            }
        }
    }

    @Test
    fun `hentFradragAlderspensjon returnerer 400 når periode er mer enn 1 år tilbake`() {
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(sakId) } doReturn SakInfo(sakId, Saksnummer(124234L), fnr1, Sakstype.UFØRE).right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = clock,
                    services = TestServicesBuilder.services(person = personService, sakService = sakService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/fradrag/eksternt/$sakId/alderspensjon",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request(periodeMerEnnEttÅrTilbake)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldBe """{"message":"Pesys støtter kun oppslag 1 år tilbake i tid","code":"maks_ett_år_tilbake"}"""
            }
        }
    }

    @Test
    fun `hentFradragFraUføretrygd returnerer 500 når pesysClient feiler`() {
        val pesysClient = mock<PesysClient> {
            on { hentVedtakForPersonPaaDatoUføre(any(), any()) } doReturn ClientError(500, "Pesys error").left()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(sakId) } doReturn SakInfo(sakId, Saksnummer(124234L), fnr1, Sakstype.UFØRE).right()
        }
        val clients = TestClientsBuilder(clock, mockedDb(), pesysClient = pesysClient).build(applicationConfig())

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = clock,
                    clients = clients,
                    services = TestServicesBuilder.services(person = personService, sakService = sakService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/fradrag/eksternt/$sakId/uforetrygd",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request(gyldigPeriode)))
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldBe """{"message":"Kunne ikke hente data fra Pesys","code":"external_service_error"}"""
                contentType() shouldBe ContentType.Application.Json
            }
        }
    }

    @Test
    fun `hentFradragFraArbeidsavklaringspenger returnerer 500 når aapClient feiler`() {
        val aapClient = mock<AapApiInternClient> {
            on { hentMaksimumUtenUtbetaling(any(), any(), any()) } doReturn ClientError(500, "AAP error").left()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(any(), any()) } doReturn Unit.right()
        }
        val sakService = mock<SakService> {
            on { hentSakInfo(sakId) } doReturn SakInfo(sakId, Saksnummer(124234L), fnr1, Sakstype.UFØRE).right()
        }
        val clients = TestClientsBuilder(clock, mockedDb(), aapApiInternClient = aapClient).build(applicationConfig())

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = clock,
                    clients = clients,
                    services = TestServicesBuilder.services(person = personService, sakService = sakService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/fradrag/eksternt/$sakId/arbeidsavklaringspenger",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request(gyldigPeriode)))
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldBe """{"message":"Kunne ikke hente data fra AAP","code":"external_service_error"}"""
                contentType() shouldBe ContentType.Application.Json
            }
        }
    }

    @Test
    fun `hentFradragAlderspensjon krever saksbehandler eller attestant`() {
        listOf(Brukerrolle.Veileder, Brukerrolle.Drift).forEach { rolle ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb(clock = clock)
                }

                defaultRequest(
                    method = HttpMethod.Post,
                    uri = "/fradrag/eksternt/$sakId/alderspensjon",
                    roller = listOf(rolle),
                ) {
                    setBody(serialize(request(gyldigPeriode)))
                }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    private fun request(periode: Periode): HentFradragRequest {
        return HentFradragRequest(
            fnr = fnr1,
            periode = periode,
        )
    }

    private fun periode(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): Periode {
        return Periode.create(
            fraOgMed = fraOgMed.atDay(1),
            tilOgMed = tilOgMed.atEndOfMonth(),
        )
    }
}
