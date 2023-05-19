package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
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
        val stansAvYtelseServiceMock = mock<StansYtelseService> {
            on { stansAvYtelse(any()) } doReturn enRevurdering.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = mockServices.copy(
                        stansYtelse = stansAvYtelseServiceMock,
                    ),
                )
            }
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
                status shouldBe HttpStatusCode.Created
            }
        }
    }

    @Test
    fun `svarer med 400 ved forsøk å iverksetting av ugyldig revurdering`() {
        val enRevurdering = beregnetRevurdering(
            clock = tikkendeFixedClock(),
        ).second
        val stansAvYtelseServiceMock = mock<StansYtelseService> {
            on {
                iverksettStansAvYtelse(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeIverksetteStansYtelse.UgyldigTilstand(enRevurdering::class).left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = mockServices.copy(
                        stansYtelse = stansAvYtelseServiceMock,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/stans/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "kunne_ikke_iverksette_stans_ugyldig_tilstand"
            }
        }
    }

    @Test
    fun `svarer med 500 hvis utbetaling feiler`() {
        val enRevurdering = beregnetRevurdering(
            clock = tikkendeFixedClock(),
        ).second
        val stansAvYtelseServiceMock = mock<StansYtelseService> {
            on {
                iverksettStansAvYtelse(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(
                UtbetalStansFeil.KunneIkkeUtbetale(
                    UtbetalingFeilet.Protokollfeil,
                ),
            ).left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = mockServices.copy(
                        stansYtelse = stansAvYtelseServiceMock,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "saker/${enRevurdering.sakId}/revurderinger/stans/${enRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant),
            ).apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldContain "kunne_ikke_utbetale"
            }
        }
    }

    @Test
    fun `svarer med 200 ved oppdatering av eksisterende revurdering`() {
        val eksisterende = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val stansAvYtelseServiceMock = mock<StansYtelseService> {
            doAnswer {
                val args = (it.arguments[0] as StansYtelseRequest.Oppdater)
                eksisterende.copy(
                    periode = Periode.create(args.fraOgMed, eksisterende.periode.tilOgMed),
                    revurderingsårsak = args.revurderingsårsak,
                ).right()
            }.whenever(mock).stansAvYtelse(any())
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = mockServices.copy(
                        stansYtelse = stansAvYtelseServiceMock,
                    ),
                )
            }
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
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldContain "2021-01-01"
                bodyAsText() shouldContain "kebabeluba"
            }
        }
    }

    @Test
    fun `svarer med 400 ved ugyldig input`() {
        val enRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val stansAvYtelseServiceMock = mock<StansYtelseService> {
            on { stansAvYtelse(any()) } doReturn enRevurdering.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = mockServices.copy(
                        stansYtelse = stansAvYtelseServiceMock,
                    ),
                )
            }
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
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain """"code":"revurderingsårsak_ugyldig_begrunnelse""""
            }
        }
    }

    @Test
    fun `svarer med 500 hvis simulering ikke går bra`() {
        val stansAvYtelseServiceMock = mock<StansYtelseService> {
            on { stansAvYtelse(any()) } doReturn KunneIkkeStanseYtelse.SimuleringAvStansFeilet(SimulerStansFeilet.KunneIkkeSimulere(SimulerUtbetalingFeilet.FeilVedSimulering(SimuleringFeilet.OppdragEksistererIkke))).left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = mockServices.copy(
                        stansYtelse = stansAvYtelseServiceMock,
                    ),
                )
            }
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
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldContain """"code":"simulering_feilet_oppdraget_finnes_ikke""""
            }
        }
    }
}
