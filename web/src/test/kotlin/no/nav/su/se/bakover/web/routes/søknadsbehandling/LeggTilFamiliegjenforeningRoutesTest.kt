package no.nav.su.se.bakover.web.routes.søknadsbehandling

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
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
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

internal class LeggTilFamiliegjenforeningRoutesTest {

    @Test
    fun `ugyldig body`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/familiegjenforening",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "status": "ugyldig status"
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
    fun `ok request`() {
        val vilkårsvurdert = søknadsbehandlingVilkårsvurdertInnvilget(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder()).second
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services().copy(
                        søknadsbehandling = mock {
                            on { leggTilFamiliegjenforeningvilkår(any()) } doReturn vilkårsvurdert.right()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/familiegjenforening",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                        "vurderinger": [
                            {
                                "periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"},
                                "status": "VilkårOppfylt"
                            }
                        ]
                    }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.Created
                JSONAssert.assertEquals(
                    bodyAsText(),
                    serialize(vilkårsvurdert.toJson(satsFactoryTestPåDato())),
                    true,
                )
            }
        }
    }
}
