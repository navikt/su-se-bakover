package no.nav.su.se.bakover.web.routes.drift

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.service.statistikk.ErstattetSakStatistikk
import no.nav.su.se.bakover.service.statistikk.ForhåndsvisErstattSakStatistikk
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryGateway
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryService
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryServiceImpl
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class SakStatistikkRouteTest {
    @Test
    fun `kun Drift har tilgang til erstatningsendepunktene`() {
        val endepunkter = listOf(
            "$DRIFT_PATH/statistikk/sak/erstatt",
            "$DRIFT_PATH/statistikk/sak/erstatt/forhandsvis",
        )

        endepunkter.forEach { endepunkt ->
            Brukerrolle.entries.filterNot { it == Brukerrolle.Drift }.forEach { rolle ->
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
                    }

                    defaultRequest(
                        method = HttpMethod.Post,
                        uri = endepunkt,
                        roller = listOf(rolle),
                    ) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"sekvensIder":[636]}""")
                    }.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `erstatter sakstatistikk for oppgitte sekvens-ID-er`() {
        val sekvensIder = listOf(1L, 2L)
        val forventetResultat = ErstattetSakStatistikk(sekvensIder.size)
        val request = ErstattSakStatistikkRequest(sekvensIder)
        val service = mock<SakStatistikkBigQueryService>()
        whenever(service.erstattSakStatistikk(sekvensIder))
            .thenReturn(forventetResultat.right())

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(sakStatistikkBigQueryService = service),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$DRIFT_PATH/statistikk/sak/erstatt",
                roller = listOf(Brukerrolle.Drift),
            ) {
                contentType(ContentType.Application.Json)
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<ErstattetSakStatistikk>(bodyAsText()) shouldBe forventetResultat
            }
        }

        verify(service).erstattSakStatistikk(sekvensIder)
    }

    @Test
    fun `forhåndsviser med ekte service og lokal BigQuery-gateway`() {
        val sekvensIder = listOf(123L)
        val unikeSekvensIder = sekvensIder.toSet()
        val request = ErstattSakStatistikkRequest(sekvensIder)
        val forventetResultat = ForhåndsvisErstattSakStatistikk(
            kanErstattes = false,
            antallForespurte = sekvensIder.size,
            antallRaderISakStatistikk = 0,
            antallRaderIBigQuery = 0,
            manglendeISakStatistikk = sekvensIder,
            ikkeUnikeISakStatistikk = emptyList(),
        )
        val repo = mock<SakStatistikkRepo>()
        whenever(repo.hentSakStatistikk(unikeSekvensIder)).thenReturn(emptyList())
        val service = SakStatistikkBigQueryServiceImpl(
            repo = repo,
            bigQueryGateway = SakStatistikkBigQueryGateway.inMemory(),
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(sakStatistikkBigQueryService = service),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$DRIFT_PATH/statistikk/sak/erstatt/forhandsvis",
                roller = listOf(Brukerrolle.Drift),
            ) {
                contentType(ContentType.Application.Json)
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<ForhåndsvisErstattSakStatistikk>(bodyAsText()) shouldBe forventetResultat
            }
        }

        verify(repo).hentSakStatistikk(unikeSekvensIder)
    }

    @Test
    fun `returnerer teknisk feil når forhåndsvisningen feiler`() {
        val sekvensIder = listOf(636L)
        val request = ErstattSakStatistikkRequest(sekvensIder)
        val forventetFeil = ErrorJson(
            message = "Kunne ikke hente grunnlaget for forhåndsvisningen.",
            code = "forhandsvisning_sak_statistikk_feilet",
        )
        val service = mock<SakStatistikkBigQueryService>()
        whenever(service.forhåndsvisErstattSakStatistikk(sekvensIder))
            .thenThrow(IllegalStateException("BigQuery feilet"))

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(sakStatistikkBigQueryService = service),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "$DRIFT_PATH/statistikk/sak/erstatt/forhandsvis",
                roller = listOf(Brukerrolle.Drift),
            ) {
                contentType(ContentType.Application.Json)
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                deserialize<ErrorJson>(bodyAsText()) shouldBe forventetFeil
            }
        }
    }

    private data class ErstattSakStatistikkRequest(
        val sekvensIder: List<Long>,
    )
}
