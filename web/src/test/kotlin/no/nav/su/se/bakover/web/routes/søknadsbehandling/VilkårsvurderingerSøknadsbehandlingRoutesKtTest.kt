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
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.requestSomAttestant
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class VilkårsvurderingerSøknadsbehandlingRoutesKtTest {

    private val navIdentSaksbehandler = "random-saksbehandler-id"
    private val navIdentAttestant = "random-attestant-id"

    @Nested
    inner class `Henting av behandling` {
        @Test
        fun `driftspersonell har ikke lov til å hente søknadsbehandlinger`() {
            testApplication {
                application { testSusebakoverWithMockedDb() }
                repeat(
                    Brukerrolle.entries.filterNot { it == Brukerrolle.Veileder || it == Brukerrolle.Drift }.size,
                ) {
                    defaultRequest(
                        HttpMethod.Get,
                        "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}",
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
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = SøknadsbehandlingServices(
                                søknadsbehandlingService = mock {
                                    on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertInnvilget().second.right()
                                },
                                iverksettSøknadsbehandlingService = mock(),
                            ),
                        ),
                    )
                }
                repeat(
                    Brukerrolle.entries.filterNot { it == Brukerrolle.Veileder || it == Brukerrolle.Drift }.size,
                ) {
                    defaultRequest(
                        method = HttpMethod.Get,
                        uri = "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}",
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
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { sendTilAttestering(any()) } doReturn KunneIkkeSendeSøknadsbehandlingTilAttestering.KunneIkkeOppretteOppgave.left()
                            },
                            iverksettSøknadsbehandlingService = mock(),
                        ),
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/tilAttestering",
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
    inner class `Underkjenning av behandling` {
        @Test
        fun `Forbidden når bruker ikke er attestant`() {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                defaultRequest(
                    HttpMethod.Patch,
                    "$SAK_PATH/rubbish/behandlinger/${UUID.randomUUID()}/underkjenn",
                    listOf(Brukerrolle.Saksbehandler),
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                }

                defaultRequest(
                    HttpMethod.Patch,
                    "$SAK_PATH/${UUID.randomUUID()}/behandlinger/rubbish/underkjenn",
                    listOf(Brukerrolle.Saksbehandler),
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                }

                defaultRequest(
                    HttpMethod.Patch,
                    "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
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
                    testSusebakoverWithMockedDb()
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$SAK_PATH/rubbish/behandlinger/${UUID.randomUUID()}/underkjenn",
                    navIdentSaksbehandler,
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                }

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$SAK_PATH/${UUID.randomUUID()}/behandlinger/rubbish/underkjenn",
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
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = SøknadsbehandlingServices(
                                søknadsbehandlingService = mock {
                                    on { underkjenn(any()) } doReturn KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeBehandling.left()
                                },
                                iverksettSøknadsbehandlingService = mock(),
                            ),
                        ),
                    )
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
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
                application { testSusebakoverWithMockedDb() }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
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
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = SøknadsbehandlingServices(
                                søknadsbehandlingService = mock { on { underkjenn(any()) } doReturn KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left() },
                                iverksettSøknadsbehandlingService = mock(),
                            ),
                        ),
                    )
                }
                client.patch("$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn") {
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
                    testSusebakoverWithMockedDb(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = SøknadsbehandlingServices(
                                søknadsbehandlingService = mock {
                                    on { underkjenn(any()) } doReturn underkjentSøknadsbehandlingUføre().second.right()
                                },
                                iverksettSøknadsbehandlingService = mock(),
                            ),
                        ),
                    )
                }
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/underkjenn",
                    navIdentAttestant,
                ) {
                    setBody("""{"kommentar":"kommentar", "grunn": "BEREGNINGEN_ER_FEIL" }""")
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    deserialize<SøknadsbehandlingJson>(bodyAsText()).let {
                        it.status shouldBe "UNDERKJENT_INNVILGET"
                    }
                }
            }
        }
    }
}
