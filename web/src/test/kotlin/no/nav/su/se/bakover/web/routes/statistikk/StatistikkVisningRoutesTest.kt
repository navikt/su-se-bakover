package no.nav.su.se.bakover.web.routes.statistikk

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsvalg
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import no.nav.su.se.bakover.service.statistikk.SakStatistikkMetadata
import no.nav.su.se.bakover.service.statistikk.SakStatistikkOppsummering
import no.nav.su.se.bakover.service.statistikk.SakstatistikkSvar
import no.nav.su.se.bakover.service.statistikk.StatistikkVisningService
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkOppsummering
import no.nav.su.se.bakover.service.statistikk.StønadstatistikkSvar
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.YearMonth

internal class StatistikkVisningRoutesTest {
    private val fraOgMed = LocalDate.of(2026, 1, 1)
    private val tilOgMed = fraOgMed.plusMonths(1).minusDays(1)
    private val oppløsning = Statistikkoppløsning.MÅNED
    private val nøkkel = SakStatistikkVisningsvalg(fraOgMed, tilOgMed, oppløsning)
    private val fraOgMedMåned = YearMonth.from(fraOgMed)
    private val tilOgMedMåned = YearMonth.from(tilOgMed)
    private val sakUri = "/statistikk/sak?fraOgMed=$fraOgMed&tilOgMed=$tilOgMed&opplosning=maned"
    private val stønadUri = "/statistikk/stønad?fraOgMed=$fraOgMedMåned&tilOgMed=$tilOgMedMåned"
    private val endepunkter = listOf(sakUri, stønadUri)

    @ParameterizedTest
    @EnumSource(Brukerrolle::class)
    fun `bare statistikk saksbehandler og drift kan hente statistikk`(rolle: Brukerrolle) {
        val roller = listOf(rolle)
        val harTilgang = when (rolle) {
            Brukerrolle.Statistikk, Brukerrolle.Saksbehandler, Brukerrolle.Drift -> true
            Brukerrolle.Attestant, Brukerrolle.Veileder -> false
        }
        val sakOppsummering = SakStatistikkOppsummering(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            oppløsning = oppløsning,
            metadata = SakStatistikkMetadata(
                maksSekvensId = null,
                sisteHendelseTidspunkt = null,
                antallBehandlinger = 0,
                behandlingerMedFlereUtfall = 0,
            ),
            perioder = emptyList(),
            kohorter = emptyList(),
        )
        val stønadOppsummering = StønadStatistikkOppsummering(
            fraOgMed = fraOgMedMåned,
            tilOgMed = tilOgMedMåned,
            perioder = emptyList(),
        )
        val service = mock<StatistikkVisningService>()
        whenever(service.hentSakstatistikk(nøkkel)).thenReturn(SakstatistikkSvar.Ferdig(sakOppsummering))
        whenever(service.hentStønadstatistikk(fraOgMedMåned, tilOgMedMåned))
            .thenReturn(StønadstatistikkSvar.Ferdig(stønadOppsummering))

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(statistikkVisningService = service),
                )
            }

            val sakSvar = defaultRequest(HttpMethod.Get, sakUri, roller)
            val stønadSvar = defaultRequest(HttpMethod.Get, stønadUri, roller)
            if (harTilgang) {
                sakSvar.status shouldBe HttpStatusCode.OK
                deserialize<SakStatistikkOppsummering>(sakSvar.bodyAsText()) shouldBe sakOppsummering
                stønadSvar.status shouldBe HttpStatusCode.OK
                deserialize<StønadStatistikkOppsummering>(stønadSvar.bodyAsText()) shouldBe stønadOppsummering
            } else {
                listOf(sakSvar, stønadSvar).forEach { svar ->
                    svar.status shouldBe HttpStatusCode.Forbidden
                    deserialize<ErrorJson>(svar.bodyAsText()).code shouldBe "mangler_rolle"
                }
            }
        }

        if (harTilgang) {
            verify(service).hentSakstatistikk(nøkkel)
            verify(service).hentStønadstatistikk(fraOgMedMåned, tilOgMedMåned)
            verifyNoMoreInteractions(service)
        } else {
            verifyNoInteractions(service)
        }
    }

    @Test
    fun `statistikk krever token og medlemskap i en tillatt gruppe`() {
        val service = mock<StatistikkVisningService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(statistikkVisningService = service),
                )
            }

            endepunkter.forEach { uri ->
                client.get(uri).status shouldBe HttpStatusCode.Unauthorized
                defaultRequest(HttpMethod.Get, uri, roller = emptyList()).status shouldBe HttpStatusCode.Unauthorized
            }
        }
        verifyNoInteractions(service)
    }
}
