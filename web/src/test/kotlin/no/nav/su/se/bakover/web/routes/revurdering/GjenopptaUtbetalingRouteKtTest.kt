package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseRequest
import no.nav.su.se.bakover.service.revurdering.KunneIkkeGjenopptaYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.utbetaling.SimulerGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

internal class GjenopptaUtbetalingRouteKtTest {

    private val mockServices = TestServicesBuilder.services()

    @Test
    fun `svarer med 201 ved påbegynt gjenopptak av utbetaling`() {
        val enRevurdering = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse()
            .second
        val revurderingServiceMock = mock<RevurderingService>() {
            on { gjenopptaYtelse(any()) } doReturn enRevurdering.right()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = mockServices.copy(
                        revurdering = revurderingServiceMock,
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "MOTTATT_KONTROLLERKLÆRING",
                          "begrunnelse": "huffda"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
            }
        }
    }

    @Test
    fun `svarer med 400 ved forsøk å iverksetting av ugyldig revurdering`() {
        val enRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
        val revurderingServiceMock = mock<RevurderingService>() {
            on {
                iverksettGjenopptakAvYtelse(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeIverksetteGjenopptakAvYtelse.UgyldigTilstand(
                enRevurdering::class,
            ).left()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = mockServices.copy(
                        revurdering = revurderingServiceMock,
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "kunne_ikke_iverksette_gjenopptak_ugyldig_tilstand"
            }
        }
    }

    @Test
    fun `svarer med 500 hvis utbetaling feiler`() {
        val enRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
        val revurderingServiceMock = mock<RevurderingService>() {
            on {
                iverksettGjenopptakAvYtelse(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale(
                UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte),
            ).left()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = mockServices.copy(
                        revurdering = revurderingServiceMock,
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "kontrollsimulering_ulik_saksbehandlers_simulering"
            }
        }
    }

    @Test
    fun `svarer med 200 ved oppdatering av eksisterende revurdering`() {
        val eksisterende = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse()
        val simulertRevurdering = eksisterende.second
        val sisteVedtak = eksisterende.first.vedtakListe.last()

        val revurderingServiceMock = mock<RevurderingService>() {
            doAnswer {
                val args = (it.arguments[0] as GjenopptaYtelseRequest.Oppdater)
                simulertRevurdering.copy(
                    periode = Periode.create(sisteVedtak.periode.fraOgMed, simulertRevurdering.periode.tilOgMed),
                    revurderingsårsak = args.revurderingsårsak,
                ).right()
            }.whenever(mock).gjenopptaYtelse(any())
        }
        withTestApplication(
            {
                testSusebakover(
                    services = mockServices.copy(
                        revurdering = revurderingServiceMock,
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Patch,
                "saker/${simulertRevurdering.sakId}/revurderinger/gjenoppta/${simulertRevurdering.id}",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-08-01",
                          "årsak": "MOTTATT_KONTROLLERKLÆRING",
                          "begrunnelse": "kebabeluba"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                response.content shouldContain "2021-08-01"
                response.content shouldContain "kebabeluba"
            }
        }
    }

    @Test
    fun `svarer med 400 ved ugyldig input`() {
        val enRevurdering = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse()
            .second
        val revurderingServiceMock = mock<RevurderingService>() {
            on { gjenopptaYtelse(any()) } doReturn enRevurdering.right()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = mockServices.copy(
                        revurdering = revurderingServiceMock,
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "KJEKS",
                          "begrunnelse": "huffda"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain """"code":"revurderingsårsak_ugyldig_årsak""""
            }
        }
    }

    @Test
    fun `svarer med 500 ved forsøk på gjenopptak av opphørt periode`() {
        val revurderingServiceMock = mock<RevurderingService>() {
            on { gjenopptaYtelse(any()) } doReturn KunneIkkeGjenopptaYtelse.KunneIkkeSimulere(
                SimulerGjenopptakFeil.KunneIkkeGenerereUtbetaling(
                    Utbetalingsstrategi.Gjenoppta.Feil.KanIkkeGjenopptaOpphørtePeriode,
                ),
            ).left()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = mockServices.copy(
                        revurdering = revurderingServiceMock,
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "saker/${UUID.randomUUID()}/revurderinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "MOTTATT_KONTROLLERKLÆRING",
                          "begrunnelse": "huffda"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain """"code":"kan_ikke_gjenoppta_opphørte_utbetalinger""""
            }
        }
    }
}
