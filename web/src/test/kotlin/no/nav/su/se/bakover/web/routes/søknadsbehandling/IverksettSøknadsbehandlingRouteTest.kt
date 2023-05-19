package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.requestSomAttestant
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class IverksettSøknadsbehandlingRouteTest {

    private val navIdentSaksbehandler = "random-saksbehandler-id"
    private val navIdentAttestant = "random-attestant-id"

    @Test
    fun `Forbidden når den som behandlet saken prøver å attestere seg selv`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock(),
                            iverksettSøknadsbehandlingService = mock {
                                on { iverksett(any<IverksettSøknadsbehandlingCommand>()) } doReturn KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                            },
                        ),
                    ),
                )
            }
            client.patch("$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett") {
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant),
                        navIdent = navIdentSaksbehandler,
                    ).asBearerToken(),
                )
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
                            søknadsbehandlingService = mock(),
                            iverksettSøknadsbehandlingService = mock {
                                on { iverksett(any<IverksettSøknadsbehandlingCommand>()) } doReturn iverksattSøknadsbehandlingUføre().right()
                            },
                        ),
                    ),
                )
            }
            client.patch("$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett") {
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant),
                        navIdent = navIdentAttestant,
                    ).asBearerToken(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Feiler dersom man ikke får sendt til utbetaling`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock(),
                            iverksettSøknadsbehandlingService = mock {
                                on { iverksett(any<IverksettSøknadsbehandlingCommand>()) } doReturn KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale(
                                    UtbetalingFeilet.Protokollfeil,
                                )
                                    .left()
                            },
                        ),
                    ),
                )
            }
            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett",
            ).apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldContain "Kunne ikke utføre utbetaling"
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
                HttpMethod.Patch,
                "$sakPath/rubbish/behandlinger/${UUID.randomUUID()}/iverksett",
                listOf(Brukerrolle.Saksbehandler),
                navIdentSaksbehandler,
            ).apply {
                status shouldBe HttpStatusCode.Forbidden
            }

            defaultRequest(
                HttpMethod.Patch,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.Forbidden
            }
        }
    }
}
