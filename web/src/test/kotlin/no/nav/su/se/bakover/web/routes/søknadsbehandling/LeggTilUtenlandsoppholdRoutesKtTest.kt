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
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
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
                testSusebakoverWithMockedDb()
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
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
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { leggTilUtenlandsopphold(any(), any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()
                            },
                            iverksettSøknadsbehandlingService = mock(),
                        ),
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
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
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { leggTilUtenlandsopphold(any(), any()) } doReturn vilkårsvurdert.right()
                            },
                            iverksettSøknadsbehandlingService = mock(),
                        ),
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
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
                    serialize(vilkårsvurdert.toJson(formuegrenserFactoryTestPåDato())),
                    true,
                )
            }
        }
    }

    @Test
    fun `feilmelding for ugyldig periode`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/utenlandsopphold",
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
