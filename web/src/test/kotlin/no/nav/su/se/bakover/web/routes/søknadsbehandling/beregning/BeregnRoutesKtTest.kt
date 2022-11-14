package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class BeregnRoutesKtTest {

    @Test
    fun `svarer med CREATED hvis suksess`() {
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        søknadsbehandling = SøknadsbehandlingServices(
                            søknadsbehandlingService = mock {
                                on { beregn(any()) } doReturn beregnetSøknadsbehandlingUføre().second.right()
                            },
                            iverksettSøknadsbehandlingService = mock(),
                        ),
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("{}")
            }.apply {
                status shouldBe HttpStatusCode.Created
                val behandlingJson = deserialize<BehandlingJson>(body())
                behandlingJson.beregning!!.fraOgMed shouldBe stønadsperiode2021.periode.fraOgMed.toString()
                behandlingJson.beregning.tilOgMed shouldBe stønadsperiode2021.periode.tilOgMed.toString()
                behandlingJson.beregning.månedsberegninger shouldHaveSize 12
            }
        }
    }

    @Test
    fun `feilresponser fra beregn-endepunkt`() {
        val behandlingEksisterIkke = UUID.randomUUID()

        testApplication {
            // ugyldig søknadsbehandling id
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/blabla/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                application {
                    testSusebakover(
                        services = TestServicesBuilder.services(
                            søknadsbehandling = SøknadsbehandlingServices(
                                søknadsbehandlingService = mock {
                                    on { beregn(any()) } doReturn SøknadsbehandlingService.KunneIkkeBeregne.FantIkkeBehandling.left()
                                },
                                iverksettSøknadsbehandlingService = mock(),
                            ),
                        ),
                    )
                }
            }.apply {
                assertSoftly {
                    status shouldBe HttpStatusCode.BadRequest
                    body<String>() shouldContain "ikke en gyldig UUID"
                }
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/$behandlingEksisterIkke/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("{}")
            }.apply {
                assertSoftly {
                    status shouldBe HttpStatusCode.NotFound
                    body<String>() shouldContain "Fant ikke behandling"
                }
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${UUID.randomUUID()}/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                assertSoftly {
                    status shouldBe HttpStatusCode.BadRequest
                    body<String>() shouldContain "Ugyldig body"
                }
            }
        }
    }
}
