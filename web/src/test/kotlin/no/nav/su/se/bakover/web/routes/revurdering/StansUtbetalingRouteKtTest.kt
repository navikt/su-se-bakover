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
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
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

internal class StansUtbetalingRouteKtTest {

    private val mockServices = TestServicesBuilder.services()

    @Test
    fun `svarer med 201 ved påbegynt stans av utbetaling`() {
        val enRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val revurderingServiceMock = mock<RevurderingService>() {
            on { stansAvYtelse(any()) } doReturn enRevurdering.right()
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
                "saker/${enRevurdering.sakId}/revurderinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "MANGLENDE_KONTROLLERKLÆRING",
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
            on { iverksettStansAvYtelse(any(), any()) } doReturn KunneIkkeIverksetteStansYtelse.UgyldigTilstand(
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
                "saker/${enRevurdering.sakId}/revurderinger/stans/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "kunne_ikke_iverksette_stans_ugyldig_tilstand"
            }
        }
    }

    @Test
    fun `svarer med 500 hvis utbetaling feiler`() {
        val enRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
        val revurderingServiceMock = mock<RevurderingService>() {
            on { iverksettStansAvYtelse(any(), any()) } doReturn KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(
                UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil),
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
                "saker/${enRevurdering.sakId}/revurderinger/stans/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "kunne_ikke_utbetale"
            }
        }
    }

    @Test
    fun `svarer med 200 ved oppdatering av eksisterende revurdering`() {
        val eksisterende = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val revurderingServiceMock = mock<RevurderingService>() {
            doAnswer {
                val args = (it.arguments[0] as StansYtelseRequest.Oppdater)
                eksisterende.copy(
                    periode = Periode.create(args.fraOgMed, eksisterende.periode.tilOgMed),
                    revurderingsårsak = args.revurderingsårsak,
                ).right()
            }.whenever(mock).stansAvYtelse(any())
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
                "saker/${eksisterende.sakId}/revurderinger/stans/${eksisterende.id}",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-01-01",
                          "årsak": "MANGLENDE_KONTROLLERKLÆRING",
                          "begrunnelse": "kebabeluba"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                response.content shouldContain "2021-01-01"
                response.content shouldContain "kebabeluba"
            }
        }
    }

    @Test
    fun `svarer med 400 ved ugyldig input`() {
        val enRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val revurderingServiceMock = mock<RevurderingService>() {
            on { stansAvYtelse(any()) } doReturn enRevurdering.right()
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
                "saker/${enRevurdering.sakId}/revurderinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "MANGLENDE_KONTROLLERKLÆRING",
                          "begrunnelse": ""
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain """"code":"revurderingsårsak_ugyldig_begrunnelse""""
            }
        }
    }

    @Test
    fun `svarer med 500 hvis simulering ikke går bra`() {
        val revurderingServiceMock = mock<RevurderingService>() {
            on { stansAvYtelse(any()) } doReturn KunneIkkeStanseYtelse.SimuleringAvStansFeilet(
                SimulerStansFeilet.KunneIkkeSimulere(
                    SimuleringFeilet.OPPDRAGET_FINNES_IKKE,
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
                "saker/${UUID.randomUUID()}/revurderinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fraOgMed": "2021-05-01",
                          "årsak": "MANGLENDE_KONTROLLERKLÆRING",
                          "begrunnelse": "huffda"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain """"code":"simulering_feilet_oppdraget_finnes_ikke""""
            }
        }
    }
}
