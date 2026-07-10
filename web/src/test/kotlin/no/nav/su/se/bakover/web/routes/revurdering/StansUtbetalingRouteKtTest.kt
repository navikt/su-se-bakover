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
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import økonomi.domain.simulering.SimulerStansFeilet
import økonomi.domain.simulering.SimuleringFeilet
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
                    serialize(
                        StansUtbetalingBody(
                            fraOgMed = 1.mai(2021),
                            årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.name,
                            begrunnelse = "huffda",
                        ),
                    ),
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
            } doReturn KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale.left()
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
                bodyAsText() shouldContain "Kunne ikke sende til oppdrag"
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
                    periode = Periode.create(args.fraOgMed.dato, eksisterende.periode.tilOgMed),
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
                    serialize(
                        StansUtbetalingBody(
                            fraOgMed = 1.januar(2021),
                            årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.name,
                            begrunnelse = "kebabeluba",
                        ),
                    ),
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
                    serialize(
                        StansUtbetalingBody(
                            fraOgMed = 1.mai(2021),
                            årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.name,
                            begrunnelse = "",
                        ),
                    ),
                )
            }.apply {
                val forventetKode = deserialize<ErrorJson>(
                    Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse.tilResultat().json,
                ).code
                status shouldBe HttpStatusCode.BadRequest
                deserialize<ErrorJson>(bodyAsText()).code shouldBe forventetKode
            }
        }
    }

    @Test
    fun `svarer med 400 ved opprettelse av stans når fraOgMed ikke er første dag i måneden`() {
        val enRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val stansAvYtelseServiceMock = mock<StansYtelseService>()
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
                    serialize(
                        StansUtbetalingBody(
                            fraOgMed = 2.mai(2021),
                            årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.name,
                            begrunnelse = "huffda",
                        ),
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain """"code":"ugyldig_fra_og_med""""
            }
        }
        verifyNoInteractions(stansAvYtelseServiceMock)
    }

    @Test
    fun `svarer med 400 ved oppdatering av stans når fraOgMed ikke er første dag i måneden`() {
        val eksisterende = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val stansAvYtelseServiceMock = mock<StansYtelseService>()
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
                    serialize(
                        StansUtbetalingBody(
                            fraOgMed = 2.mai(2021),
                            årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.name,
                            begrunnelse = "kebabeluba",
                        ),
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain """"code":"ugyldig_fra_og_med""""
            }
        }
        verifyNoInteractions(stansAvYtelseServiceMock)
    }

    @Test
    fun `svarer med 500 hvis simulering ikke går bra`() {
        val stansAvYtelseServiceMock = mock<StansYtelseService> {
            on { stansAvYtelse(any()) } doReturn KunneIkkeStanseYtelse.SimuleringAvStansFeilet(
                SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil),
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
                "saker/${UUID.randomUUID()}/revurderinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    serialize(
                        StansUtbetalingBody(
                            fraOgMed = 1.mai(2021),
                            årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.name,
                            begrunnelse = "huffda",
                        ),
                    ),
                )
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldContain """"code":"simulering_feilet""""
            }
        }
    }
}
