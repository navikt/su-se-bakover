package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.HttpMethod
import io.ktor.server.server.testing.setBody
import io.ktor.server.server.testing.withTestApplication
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class LeggTilUtenlandsoppholdRoutesKtTest {
    @Test
    fun `svarer med feilmelding ved ugyldig body`() {
        testApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "bogus": "body"
                    }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                body<String>()Contain "Ugyldig body"
            }
        }
    }

    @Test
    fun `svarer med feilmelding fra service`() {
        testApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services().copy(
                        søknadsbehandling = mock {
                            on { leggTilUtenlandsopphold(any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()
                        },
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                        {
                            "periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},
                            "status": "SkalHoldeSegINorge",
                            "begrunnelse": "jawol" 
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                body<String>()Contain "Fant ikke behandling"
            }
        }
    }

    @Test
    fun `svarer med søknadsbehandling ved suksess`() {
        val vilkårsvurdert = søknadsbehandlingVilkårsvurdertInnvilget().second
        testApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services().copy(
                        søknadsbehandling = mock {
                            on { leggTilUtenlandsopphold(any()) } doReturn vilkårsvurdert.right()
                        },
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"},
                        "status": "SkalHoldeSegINorge",
                        "begrunnelse": "jawol"    
                                                   
                    }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.Created
                JSONAssert.assertEquals(response.content, serialize(vilkårsvurdert.toJson()), true)
            }
        }
    }

    @Test
    fun `feilmelding for ugyldig periode`() {
        testApplication(
            {
                testSusebakover(services = TestServicesBuilder.services())
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "periode": {"fraOgMed": "2021-05-01", "tilOgMed": "2021-01-31"},
                        "status": "SkalHoldeSegINorge",
                        "begrunnelse": "jawol"    
                                                   
                    }
                    """.trimIndent()
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                body<String>()Contain "ugyldig_periode_start_slutt"
            }
        }
    }
}
