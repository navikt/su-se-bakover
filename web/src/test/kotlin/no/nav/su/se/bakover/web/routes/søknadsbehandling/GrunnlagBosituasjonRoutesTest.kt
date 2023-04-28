package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonRequest
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class GrunnlagBosituasjonRoutesTest {

    private val services = TestServicesBuilder.services()
    private val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

    @Test
    fun `andre roller enn saksbehandler skal ikke ha tilgang til bosituasjon`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Saksbehandler }.forEach { rolle ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon",
                    listOf(rolle),
                ) {
                    setBody(
                        //language=json
                        """{ 
                            "bosituasjoner": [{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, 
                            "epsFnr": null,
                            "delerBolig": false,
                            "erEPSUførFlyktning": null}]
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `happy case deler bolig med voksne`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjongrunnlag(any(), any()) } doReturn søknadsbehandling.right()
        }
        val søknadsbehandlingServicesMock = SøknadsbehandlingServices(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            iverksettSøknadsbehandlingService = mock(),
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services.copy(søknadsbehandling = søknadsbehandlingServicesMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """{ 
                            "bosituasjoner": [{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, 
                            "epsFnr": null,
                            "delerBolig": true,
                            "erEPSUførFlyktning": null}]
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                verify(søknadsbehandlingServiceMock).leggTilBosituasjongrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonerRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjoner = listOf(
                                LeggTilBosituasjonRequest(
                                    periode = søknadsbehandling.periode,
                                    epsFnr = null,
                                    delerBolig = true,
                                    ektemakeEllerSamboerUførFlyktning = null,
                                ),
                            ),
                        )
                    },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case bor alene`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjongrunnlag(any(), any()) } doReturn søknadsbehandling.right()
        }
        val søknadsbehandlingServicesMock = SøknadsbehandlingServices(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            iverksettSøknadsbehandlingService = mock(),
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services.copy(søknadsbehandling = søknadsbehandlingServicesMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """{ 
                        "bosituasjoner": [{
                            "periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, 
                            "epsFnr": null,
                            "delerBolig": false,
                            "erEPSUførFlyktning": null
                        }]
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                verify(søknadsbehandlingServiceMock).leggTilBosituasjongrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonerRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjoner = listOf(
                                LeggTilBosituasjonRequest(
                                    periode = søknadsbehandling.periode,
                                    epsFnr = null,
                                    delerBolig = false,
                                    ektemakeEllerSamboerUførFlyktning = null,
                                ),
                            ),
                        )
                    },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case eps ufør flykning`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjongrunnlag(any(), any()) } doReturn søknadsbehandling.right()
        }
        val søknadsbehandlingServicesMock = SøknadsbehandlingServices(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            iverksettSøknadsbehandlingService = mock(),
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services.copy(søknadsbehandling = søknadsbehandlingServicesMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """{ 
                            "bosituasjoner": [{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, 
                            "epsFnr": "12345678901",
                            "delerBolig": null,
                            "erEPSUførFlyktning": true}]
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                verify(søknadsbehandlingServiceMock).leggTilBosituasjongrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonerRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjoner = listOf(
                                LeggTilBosituasjonRequest(
                                    periode = søknadsbehandling.periode,
                                    epsFnr = "12345678901",
                                    delerBolig = null,
                                    ektemakeEllerSamboerUførFlyktning = true,
                                ),
                            ),
                        )
                    },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case eps ikke ufør flykning`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjongrunnlag(any(), any()) } doReturn søknadsbehandling.right()
        }
        val søknadsbehandlingServicesMock = SøknadsbehandlingServices(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            iverksettSøknadsbehandlingService = mock(),
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services.copy(søknadsbehandling = søknadsbehandlingServicesMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """{ 
                            "bosituasjoner": [{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, 
                            "epsFnr": "12345678901",
                            "delerBolig": null,
                            "erEPSUførFlyktning": false}]
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                verify(søknadsbehandlingServiceMock).leggTilBosituasjongrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonerRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjoner = listOf(
                                LeggTilBosituasjonRequest(
                                    periode = søknadsbehandling.periode,
                                    epsFnr = "12345678901",
                                    delerBolig = null,
                                    ektemakeEllerSamboerUførFlyktning = false,
                                ),
                            ),
                        )
                    },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case eps 67 eller over`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjongrunnlag(any(), any()) } doReturn søknadsbehandling.right()
        }
        val søknadsbehandlingServicesMock = SøknadsbehandlingServices(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            iverksettSøknadsbehandlingService = mock(),
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services.copy(søknadsbehandling = søknadsbehandlingServicesMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """{ 
                            "bosituasjoner": [{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, 
                            "epsFnr": "12345678901",
                            "delerBolig": false,
                            "erEPSUførFlyktning": null}]
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                verify(søknadsbehandlingServiceMock).leggTilBosituasjongrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonerRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjoner = listOf(
                                LeggTilBosituasjonRequest(
                                    periode = søknadsbehandling.periode,
                                    epsFnr = "12345678901",
                                    delerBolig = false,
                                    ektemakeEllerSamboerUførFlyktning = null,
                                ),
                            ),
                        )
                    },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `service finner ikke behandling`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on {
                leggTilBosituasjongrunnlag(any(), any())
            } doReturn KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left()
        }
        val søknadsbehandlingServicesMock = SøknadsbehandlingServices(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            iverksettSøknadsbehandlingService = mock(),
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services.copy(søknadsbehandling = søknadsbehandlingServicesMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """{ 
                            "bosituasjoner": [{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, 
                            "epsFnr": null,
                            "delerBolig": true,
                            "erEPSUførFlyktning": null}]
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "fant_ikke_behandling"
            }
        }
    }
}
