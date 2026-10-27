package no.nav.su.se.bakover.web.routes

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.notat.NotatService
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.service.statistikk.ResendStatistikkhendelserService
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkJobService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.tilbakekreving.opprett.OpprettTilbakekrevingRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UgyldigRouteInputTest {
    @Test
    fun `alle statistikkjobber svarer accepted før arbeidet er fullført`() {
        data class DatoBody(val fraOgMed: LocalDate)
        data class VedtakBody(val vedtak: String)
        data class SakBody(val fraOgMed: LocalDate, val tilOgMed: LocalDate)
        val ferdigsignal = CountDownLatch(1)
        val antallJobber = 4
        val fullførteJobber = CountDownLatch(antallJobber)
        fun ventPåFullføring() {
            check(ferdigsignal.await(30, TimeUnit.SECONDS)) { "Jobben fikk ikke fullføringssignal" }
            fullførteJobber.countDown()
        }
        val service = mock<ResendStatistikkhendelserService> {
            on { resendIverksattSøknadsbehandling(any()) } doAnswer { ventPåFullføring() }
            on { resendStatistikkForVedtak(any(), isNull()) } doAnswer {
                ventPåFullføring()
                Unit.right()
            }
            on { resendStatistikkForSak(any(), any()) } doAnswer { ventPåFullføring() }
        }
        val stønadStatistikkService = mock<StønadStatistikkJobService> {
            on { lagStatistikkForFlereMåneder(any(), any()) } doAnswer { ventPåFullføring() }
        }
        val dato = LocalDate.of(2026, 1, 1)
        val vedtakId = UUID.randomUUID()
        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(resendStatistikkhendelserService = service)
                            .copy(stønadStatistikkJobService = stønadStatistikkService),
                    )
                }
                listOf(
                    "/drift/resend-statistikk/vedtak/søknadsbehandling" to serialize(DatoBody(dato)),
                    "/drift/resend-statistikk/vedtak" to serialize(VedtakBody(vedtakId.toString())),
                    "/drift/resend-statistikk/sak" to serialize(SakBody(dato, dato)),
                    "/drift/statistikk/stønad/lag" to serialize(SakBody(dato, dato)),
                ).forEach { (path, body) ->
                    defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Drift)) {
                        setBody(body)
                    }.apply {
                        status shouldBe HttpStatusCode.Accepted
                    }
                }
                verify(service, timeout(5000)).resendIverksattSøknadsbehandling(dato)
                verify(service, timeout(5000)).resendStatistikkForVedtak(vedtakId)
                verify(service, timeout(5000)).resendStatistikkForSak(dato, dato)
                verify(stønadStatistikkService, timeout(5000)).lagStatistikkForFlereMåneder(
                    YearMonth.from(dato),
                    YearMonth.from(dato),
                )
                fullførteJobber.count shouldBe antallJobber.toLong()
            }
        } finally {
            ferdigsignal.countDown()
        }
        check(fullførteJobber.await(10, TimeUnit.SECONDS)) { "Statistikkjobbene ble ikke avsluttet" }
    }

    @Test
    fun `notatrutene avviser ugyldige referanser før tjenestekall`() {
        data class Body(val referanseId: String, val referanseType: String)
        val notatService = mock<NotatService>()
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val referanseType = ReferanseType.entries.first().name
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services().copy(notatService = notatService))
            }
            defaultRequest(HttpMethod.Post, "/notat/$sakId", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(Body("ugyldig", referanseType)))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).code shouldBe "ugyldig_referanse_id"
            }
            listOf(
                "/notat/$sakId/ugyldig/hentNotat?referanseType=$referanseType" to "ugyldig_referanse_id",
                "/notat/$sakId/$referanseId/hentNotat" to "ugyldig_referanse_type",
                "/notat/$sakId/$referanseId/hentNotat?referanseType=ugyldig" to "ugyldig_referanse_type",
            ).forEach { (path, feilkode) ->
                defaultRequest(HttpMethod.Get, path, listOf(Brukerrolle.Saksbehandler)).apply {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<ErrorJson>(bodyAsText()).code shouldBe feilkode
                }
            }
        }
        verifyNoInteractions(notatService)
    }

    @Test
    fun `resending av statistikk validerer dato og alle identifikatorer før jobbstart`() {
        data class DatoBody(val fraOgMed: String)
        data class VedtakBody(val vedtak: String)
        val service = mock<ResendStatistikkhendelserService>()
        val gyldigId = UUID.randomUUID()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(resendStatistikkhendelserService = service),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "/drift/resend-statistikk/vedtak/søknadsbehandling",
                listOf(Brukerrolle.Drift),
            ) {
                setBody(serialize(DatoBody("2026-02-30")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).code shouldBe "ugyldig_datoformat"
            }
            listOf("", "ugyldig", "$gyldigId,ugyldig").forEach { ider ->
                defaultRequest(HttpMethod.Post, "/drift/resend-statistikk/vedtak", listOf(Brukerrolle.Drift)) {
                    setBody(serialize(VedtakBody(ider)))
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<ErrorJson>(bodyAsText()).code shouldBe "ugyldig_vedtak_id"
                }
            }
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `tilbakekrevingsrutene avviser ugyldige identifikatorer`() {
        val sakId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        testApplication {
            application { testSusebakoverWithMockedDb() }
            defaultRequest(HttpMethod.Post, "/saker/$sakId/tilbakekreving/ny", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(OpprettTilbakekrevingRequest(versjon = 0, relatertId = "ugyldig")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).code shouldBe "ugyldig_relatert_id"
            }
            defaultRequest(
                HttpMethod.Get,
                "/saker/$sakId/tilbakekreving/$behandlingId/forhandsvarsel/ugyldig",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).code shouldBe "ugyldig_dokument_id"
            }
        }
    }
}
