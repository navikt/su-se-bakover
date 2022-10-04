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
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.test.generer
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

class GrunnlagBosituasjonEpsRoutesTest {

    private val services = TestServicesBuilder.services()
    private val fnr = Fnr.generer()
    private val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

    @Test
    fun `andre roller enn saksbehandler skal ikke ha tilgang til bosituasjon`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Saksbehandler }.forEach { rolle ->
            testApplication {
                application { testSusebakover() }
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
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
    fun `happy case`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application { testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock)) }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": null}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).leggTilBosituasjonEpsgrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonEpsRequest(
                            behandlingId = søknadsbehandling.id,
                            epsFnr = null,
                        )
                    },
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case med eps`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application { testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock)) }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).leggTilBosituasjonEpsgrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonEpsRequest(
                            behandlingId = søknadsbehandling.id,
                            epsFnr = fnr,
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
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling.left()
        }

        testApplication {
            application { testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock)) }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain ("fant_ikke_behandling")
            }
        }
    }

    @Test
    fun `service klarer ikke hente person i pdl`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl.left()
        }

        testApplication {
            application { testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock)) }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain ("fant_ikke_person")
            }
        }
    }
}
