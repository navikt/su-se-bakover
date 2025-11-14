package no.nav.su.se.bakover.web.routes.fritekst

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstFeil
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class FritekstRouteTest {

    @Test
    fun `kan hente fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val fritekst = Fritekst(referanseId = referanseId, FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, fritekst = "fritekst for vedtak")
        val fritekstMockService = mock<FritekstService> {
            on { hentFritekst(referanseId = referanseId, FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, sakId = sakId) } doReturn fritekst.right()
        }

        val request = Body(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name)
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
            }
            defaultRequest(
                HttpMethod.Post,
                FRITEKST_PATH,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<Fritekst>(bodyAsText()) shouldBe fritekst
            }
        }
    }

    @Test
    fun `Får fritekst feil for ikke eksisterende fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val fritekstMockService = mock<FritekstService> {
            on { hentFritekst(referanseId = referanseId, FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, sakId = sakId) } doReturn FritekstFeil.FantIkkeFritekst.left()
        }

        val request = Body(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name)
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
            }

            defaultRequest(
                HttpMethod.Post,
                FRITEKST_PATH,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldBe FeilResponser.fantIkkeFritekst.json
            }
        }
    }

    @Test
    fun `Kan lagre fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val friteksttekst = "fritekst for vedtak"
        val fritekstDomain = FritekstDomain(referanseId = referanseId, type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, fritekst = friteksttekst, sakId = sakId)
        val fritekstMockService = mock<FritekstService>()

        doNothing()
            .`when`(fritekstMockService)
            .lagreFritekst(fritekstDomain)

        val request = FritekstRequestLagre(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name, fritekst = friteksttekst)
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
            }
            defaultRequest(
                HttpMethod.Post,
                "$FRITEKST_PATH/lagre",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
        verify(fritekstMockService).lagreFritekst(fritekstDomain)
    }

    @Test
    fun `Sjekker at lagring kun skjer hvis det blir riktig domeneobjet`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val friteksttekst = "fritekst for vedtak"

        val request = FritekstRequestLagre(referanseId = "", sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name, fritekst = friteksttekst)
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            defaultRequest(
                HttpMethod.Post,
                "$FRITEKST_PATH/lagre",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldBe FeilResponser.ugyldigBody("Ugyldig verdi for felt 'referanseId': ''").json
            }
        }
    }
}
