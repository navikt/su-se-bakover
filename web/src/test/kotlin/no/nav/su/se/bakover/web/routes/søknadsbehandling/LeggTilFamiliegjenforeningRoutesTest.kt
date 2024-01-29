package no.nav.su.se.bakover.web.routes.søknadsbehandling

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
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import java.util.UUID

internal class LeggTilFamiliegjenforeningRoutesTest {

    @Test
    fun `ugyldig body`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/familiegjenforening",
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
        val vilkårsvurdert =
            søknadsbehandlingVilkårsvurdertInnvilget(
                customVilkår = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder().vilkår.toList()
                    .filterNot { it is OpplysningspliktVilkår },
            ).second
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { leggTilFamiliegjenforeningvilkår(any(), any()) } doReturn vilkårsvurdert.right()
                            },
                            iverksettSøknadsbehandlingService = mock(),
                        ),
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/familiegjenforening",
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
                    serialize(vilkårsvurdert.toJson(formuegrenserFactoryTestPåDato())),
                    true,
                )
            }
        }
    }
}
