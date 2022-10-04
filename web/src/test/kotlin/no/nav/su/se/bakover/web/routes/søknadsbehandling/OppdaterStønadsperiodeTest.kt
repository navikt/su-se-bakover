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
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.behandlingId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class OppdaterStønadsperiodeTest {
    private val url = "$sakPath/$sakId/behandlinger/$behandlingId/stønadsperiode"
    private val services = TestServicesBuilder.services()

    @Test
    fun `svarer med 404 dersom behandling ikke finnes`() {
        val serviceMock = mock<SøknadsbehandlingService> {
            on { oppdaterStønadsperiode(any()) } doReturn SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()
        }

        testApplication {
            application { testSusebakover(services = services.copy(søknadsbehandling = serviceMock)) }
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "Fant ikke behandling"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom perioden starter tidligere enn 2021`() {
        testApplication {
            application { testSusebakover(services = services) }
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2019-01-01", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "En stønadsperiode kan ikke starte før 2021"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom perioden er lenger enn 12 måneder`() {
        testApplication {
            application { testSusebakover(services = services) }
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2022-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "En stønadsperiode kan være maks 12 måneder"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom fraOgMed ikke er første dag i måneden`() {
        testApplication {
            application { testSusebakover(services = services) }
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-15", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "Perioder kan kun starte på første dag i måneden"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom tilOgMed ikke er siste dag i måneden`() {
        testApplication {
            application { testSusebakover(services = services) }
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-15"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "Perioder kan kun avsluttes siste dag i måneden"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom tilOgMed er før fraOgMed`() {
        testApplication {
            application { testSusebakover(services = services) }
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-12-01", "tilOgMed": "2021-01-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "Startmåned må være tidligere eller lik sluttmåned"
            }
        }
    }

    @Test
    fun `svarer med 201 og søknadsbehandling hvis alt er ok`() {
        val søknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

        val serviceMock = mock<SøknadsbehandlingService> {
            on { oppdaterStønadsperiode(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application { testSusebakover(services = services.copy(søknadsbehandling = serviceMock)) }
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                status shouldBe HttpStatusCode.Created
            }
        }
    }
}
