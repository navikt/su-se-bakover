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
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
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
        testApplication {
            application {
                testSusebakover()
            }
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
                bodyAsText() shouldContain "Ugyldig body"
            }
        }
    }

    @Test
    fun `svarer med feilmelding fra service`() {
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = mock {
                            on { leggTilUtenlandsopphold(any(), any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "vurderinger": [
                            {
                                "periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-01-31"},
                                "status": "SkalHoldeSegINorge"
                            }
                        ]
                     }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "Fant ikke behandling"
            }
        }
    }

    @Test
    fun `svarer med søknadsbehandling ved suksess`() {
        val vilkårsvurdert = søknadsbehandlingVilkårsvurdertInnvilget().second
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = mock {
                            on { leggTilUtenlandsopphold(any(), any()) } doReturn vilkårsvurdert.right()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "vurderinger": [
                            {
                                "periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-01-31"},
                                "status": "SkalHoldeSegINorge"
                            }
                        ]
                     }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    bodyAsText(),
                    serialize(vilkårsvurdert.toJson(satsFactoryTestPåDato())),
                    true,
                )
            }
        }
    }

    @Test
    fun `feilmelding for ugyldig periode`() {
        testApplication {
            application {
                testSusebakover(services = TestServicesBuilder.services())
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "vurderinger": [
                            {
                                "periode": {"fraOgMed": "2021-05-01", "tilOgMed": "2021-01-31"},
                                "status": "SkalHoldeSegINorge"
                            }
                        ]
                     }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "ugyldig_periode_start_slutt"
            }
        }
    }
}
