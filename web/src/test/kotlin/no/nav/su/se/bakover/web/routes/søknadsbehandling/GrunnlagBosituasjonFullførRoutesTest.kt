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
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class GrunnlagBosituasjonFullførRoutesTest {

    private val services = TestServicesBuilder.services()
    private val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

    @Test
    fun `andre roller enn saksbehandler skal ikke ha tilgang til bosituasjon`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Saksbehandler }.forEach { rolle ->
            testApplication {
                application {
                    testSusebakover()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                    listOf(rolle),
                ) {
                    setBody("""{ "epsFnr": null}""".trimIndent())
                }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `happy case deler bolig med voksne`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "DELER_BOLIG_MED_VOKSNE", "begrunnelse": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).fullførBosituasjongrunnlag(
                    argThat {
                        it shouldBe FullførBosituasjonRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjon = BosituasjonValg.DELER_BOLIG_MED_VOKSNE,
                        )
                    },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case bor alene`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "BOR_ALENE", "begrunnelse": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).fullførBosituasjongrunnlag(
                    argThat {
                        it shouldBe FullførBosituasjonRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjon = BosituasjonValg.BOR_ALENE,
                        )
                    },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case eps ufør flykning`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "EPS_UFØR_FLYKTNING", "begrunnelse": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).fullførBosituasjongrunnlag(
                    argThat {
                        it shouldBe FullførBosituasjonRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjon = BosituasjonValg.EPS_UFØR_FLYKTNING,
                        )
                    },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case eps ikke ufør flykning`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "EPS_IKKE_UFØR_FLYKTNING", "begrunnelse": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).fullførBosituasjongrunnlag(
                    argThat {
                        it shouldBe FullførBosituasjonRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjon = BosituasjonValg.EPS_IKKE_UFØR_FLYKTNING,
                        )
                    },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case eps 67 eller over`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "EPS_67_ELLER_OVER", "begrunnelse": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).fullførBosituasjongrunnlag(
                    argThat {
                        it shouldBe FullførBosituasjonRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjon = BosituasjonValg.EPS_67_ELLER_OVER,
                        )
                    },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case begrunnelse`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "EPS_67_ELLER_OVER", "begrunnelse": "Begrunnelse"}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).fullførBosituasjongrunnlag(
                    argThat {
                        it shouldBe FullførBosituasjonRequest(
                            behandlingId = søknadsbehandling.id,
                            bosituasjon = BosituasjonValg.EPS_67_ELLER_OVER,
                        )
                    },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `service finner ikke behandling`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "DELER_BOLIG_MED_VOKSNE", "begrunnelse": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "fant_ikke_behandling"
            }
        }
    }

    @Test
    fun `service klarer ikke å lagre bosituasjon`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { fullførBosituasjongrunnlag(any()) } doReturn SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon.left()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/fullfør",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "bosituasjon": "EPS_UFØR_FLYKTNING", "begrunnelse": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "kunne_ikke_legge_til_bosituasjonsgrunnlag"
                // For å treffe denne må man prøve å fullføre en ufullstendig bosituasjon som burde ha fnr, men som ikke har det
            }
        }
    }
}
