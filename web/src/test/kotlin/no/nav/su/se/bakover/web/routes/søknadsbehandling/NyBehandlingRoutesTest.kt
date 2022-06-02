package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.stønadsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.søknadId
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class NyBehandlingRoutesTest {

    private val requestPath = "$sakPath/$sakId/behandlinger"
    private val services = TestServicesBuilder.services()

    @Test
    // TODO denne testen gir ikke så mye verdi, erstatt med komponenttest?
    fun `kan opprette behandling`() {
        val saksbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().second.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = saksbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                requestPath,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "soknadId": "$søknadId", "stønadsperiode": {"fraOgMed" : "${stønadsperiode.periode.fraOgMed}", "tilOgMed": "${stønadsperiode.periode.tilOgMed}"}}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                objectMapper.readValue<BehandlingJson>(bodyAsText())
                verify(saksbehandlingServiceMock).opprett(
                    argThat {
                        it shouldBe SøknadsbehandlingService.OpprettRequest(søknadId)
                    },
                )
                verifyNoMoreInteractions(saksbehandlingServiceMock)
            }
        }
    }
}
