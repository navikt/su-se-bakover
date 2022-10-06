package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.requestSomAttestant
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.stubs.asBearerToken
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class SøknadsbehandlingRoutesKtTest {

    val navIdentSaksbehandler = "random-saksbehandler-id"
    val navIdentAttestant = "random-attestant-id"

    @Nested
    inner class `Henting av behandling` {
        @Test
        fun `driftspersonell har ikke lov til å hente søknadsbehandlinger`() {
            testApplication {
                application { testSusebakover() }
                repeat(
                    Brukerrolle.values().filterNot { it == Brukerrolle.Veileder || it == Brukerrolle.Drift }.size,
                ) {
                    defaultRequest(
                        HttpMethod.Get,
                        "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}",
                        listOf(Brukerrolle.Veileder),
                    ).apply {
                        status shouldBe HttpStatusCode.Forbidden
                    }
                }
            }
        }

        @Test
        fun `saksbehandlere kan hente søknadsbehandlinger`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertInnvilget().second.right()
                            },
                        ),
                    )
                }
                repeat(
                    Brukerrolle.values().filterNot { it == Brukerrolle.Veileder || it == Brukerrolle.Drift }.size,
                ) {
                    defaultRequest(
                        method = HttpMethod.Get,
                        uri = "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}",
                        roller = listOf(Brukerrolle.Saksbehandler),
                    ).apply {
                        status shouldBe HttpStatusCode.OK
                    }
                }
            }
        }
    }

    @Test
    fun `opprettelse av oppgave feiler ved send til attestering`() {
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = mock {
                            on { sendTilAttestering(any()) } doReturn SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()
                        },
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/tilAttestering",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "fritekst": "Fritekst!" }""")
            }.apply {
                status shouldBe HttpStatusCode.InternalServerError
                bodyAsText() shouldContain "Kunne ikke opprette oppgave"
            }
        }
    }

    @Nested
    inner class `Iverksetting av behandling` {

        @Test
        fun `Forbidden når bruker ikke er attestant`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { hent(any()) } doReturn SøknadsbehandlingService.FantIkkeBehandling.left()
                            },
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

        @Test
        fun `BadRequest når behandlingId er ugyldig uuid eller NotFound når den ikke finnes`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { iverksett(any()) } doReturn KunneIkkeIverksette.FantIkkeBehandling.left()
                            },
                        ),
                    )
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett",
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.NotFound
                }

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/rubbish/iverksett",
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        @Test
        fun `NotFound når behandling ikke eksisterer`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { iverksett(any()) } doReturn KunneIkkeIverksette.FantIkkeBehandling.left()
                            },
                        ),
                    )
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/iverksett",
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        @Test
        fun `Forbidden når den som behandlet saken prøver å attestere seg selv`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { iverksett(any()) } doReturn KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                            },
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
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { iverksett(any()) } doReturn iverksattSøknadsbehandlingUføre().second.right()
                            },
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
    }

    @Nested
    inner class `Underkjenning av behandling` {
        @Test
        fun `Forbidden når bruker ikke er attestant`() {
            testApplication {
                application {
                    testSusebakover()
                }
                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${UUID.randomUUID()}/underkjenn",
                    listOf(Brukerrolle.Saksbehandler),
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                }

                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/rubbish/underkjenn",
                    listOf(Brukerrolle.Saksbehandler),
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                }

                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
                    listOf(Brukerrolle.Saksbehandler),
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `BadRequest når sakId eller behandlingId er ugyldig`() {
            testApplication {
                application {
                    testSusebakover()
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${UUID.randomUUID()}/underkjenn",
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                }

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/rubbish/underkjenn",
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        @Test
        fun `NotFound når behandling ikke finnes`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { underkjenn(any()) } doReturn SøknadsbehandlingService.KunneIkkeUnderkjenne.FantIkkeBehandling.left()
                            },
                        ),
                    )
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
                    navIdentSaksbehandler,
                ) {
                    setBody("""{"kommentar":"b", "grunn": "BEREGNINGEN_ER_FEIL"}""")
                }.apply {
                    bodyAsText() shouldContain "Fant ikke behandling"
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        @Test
        fun `BadRequest når kommentar ikke er oppgitt`() {
            testApplication {
                application { testSusebakover() }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
                    navIdentSaksbehandler,
                ) {
                    setBody(
                        """
                    {
                        "grunn":"BEREGNINGEN_ER_FEIL",
                        "kommentar":""
                    }
                        """.trimIndent(),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    bodyAsText() shouldContain "Må angi en begrunnelse"
                }
            }
        }

        @Test
        fun `Forbidden når saksbehandler og attestant er samme person`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock { on { underkjenn(any()) } doReturn SøknadsbehandlingService.KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left() },
                        ),
                    )
                }
                client.patch("$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn") {
                    header(
                        HttpHeaders.Authorization,
                        jwtStub.createJwtToken(
                            subject = "S123456",
                            roller = listOf(Brukerrolle.Attestant),
                            navIdent = navIdentSaksbehandler,
                        ).asBearerToken(),
                    )
                    setBody(
                        """
                    {
                        "grunn": "BEREGNINGEN_ER_FEIL",
                        "kommentar": "Ser fel ut. Men denna borde bli forbidden eftersom attestant og saksbehandler er samme."
                    }
                        """.trimIndent(),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `OK når alt er som det skal være`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { underkjenn(any()) } doReturn underkjentSøknadsbehandlingUføre().second.right()
                            },
                        ),
                    )
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
                    navIdentAttestant,
                ) {
                    setBody("""{"kommentar":"kommentar", "grunn": "BEREGNINGEN_ER_FEIL" }""")
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    deserialize<BehandlingJson>(bodyAsText()).let {
                        it.status shouldBe "UNDERKJENT_INNVILGET"
                    }
                }
            }
        }

        @Test
        fun `Feiler dersom man ikke får sendt til utbetaling`() {
            testApplication {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = mock {
                                on { iverksett(any()) } doReturn KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()
                            },
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
    }
}
