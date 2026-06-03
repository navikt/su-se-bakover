package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class IverksettSøknadsbehandlingRouteTest {

    private val navIdentSaksbehandler = "random-saksbehandler-id"
    private val navIdentAttestant = "random-attestant-id"
    private val fritekst = "fritekst"

    @Test
    fun `BadRequest når beregningen er gjort med utdatert grunnbeløp`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    // Denne vil alltid være siste g så siden denne er etter clock i søknadsbehandlingTilAttesteringInnvilget
                    // så får den ikke lov til å iverksette siden stønadsperioden for 2025 har gammel g
                    satsFactory = satsFactoryTestPåDato(1.juni(2025)),
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { hent(any()) } doReturn søknadsbehandlingTilAttesteringInnvilget(
                                    clock = fixedClockAt(
                                        15.april
                                            (2025),
                                    ),
                                    stønadsperiode = Stønadsperiode.create(år(2025)),
                                ).second.right()
                            },
                            iverksettSøknadsbehandlingService = mock(),
                        ),
                    ),
                )
            }
            client.post("$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett") {
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant),
                        navIdent = navIdentAttestant,
                    ).asBearerToken(),
                )
                setBody("""{"fritekst":"$fritekst"}""")
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "grunnbeløp_er_utdatert"
            }
        }
    }

    @Test
    fun `Forbidden når den som behandlet saken prøver å attestere seg selv`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { hent(any()) } doReturn søknadsbehandlingTilAttesteringInnvilget().second.right()
                            },
                            iverksettSøknadsbehandlingService = mock {
                                on { iverksett(any<IverksettSøknadsbehandlingCommand>()) } doReturn KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                            },
                        ),
                    ),
                )
            }
            client.post("$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett") {
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant),
                        navIdent = navIdentSaksbehandler,
                    ).asBearerToken(),
                )
                setBody("""{"fritekst":"$fritekst"}""")
            }.apply {
                status shouldBe HttpStatusCode.Forbidden
            }
        }
    }

    @Test
    fun `OK når bruker er attestant, og sak ble behandlet av en annen person`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { hent(any()) } doReturn søknadsbehandlingTilAttesteringInnvilget().second.right()
                            },
                            iverksettSøknadsbehandlingService = mock {
                                on { iverksett(any<IverksettSøknadsbehandlingCommand>()) } doReturn iverksattSøknadsbehandlingUføre().right()
                            },
                        ),
                    ),
                )
            }
            client.post("$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett") {
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant),
                        navIdent = navIdentAttestant,
                    ).asBearerToken(),
                )
                setBody("""{"fritekst":"$fritekst"}""")
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Forbidden når bruker ikke er attestant`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { hent(any()) } doReturn SøknadsbehandlingService.FantIkkeBehandling.left()
                            },
                            iverksettSøknadsbehandlingService = mock(),
                        ),
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/rubbish/behandlinger/${UUID.randomUUID()}/iverksett",
                listOf(Brukerrolle.Saksbehandler),
                navIdentSaksbehandler,
            ).apply {
                status shouldBe HttpStatusCode.Forbidden
            }

            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.Forbidden
            }
        }
    }
}
