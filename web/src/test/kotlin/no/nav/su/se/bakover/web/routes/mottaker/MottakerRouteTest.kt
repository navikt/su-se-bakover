package no.nav.su.se.bakover.web.routes.mottaker

import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.distribuering.Distribueringsadresse
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.mottaker.DistribueringsadresseRequest
import no.nav.su.se.bakover.domain.mottaker.LagreMottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.OppdaterMottaker
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

internal class MottakerRouteTest {

    @Test
    fun `get tillater saksbehandler og attestant`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerService = mock<MottakerService> {
            on { hentMottaker(any(), any(), anyOrNull()) } doReturn null.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant).forEach { rolle ->
                defaultRequest(
                    method = HttpMethod.Get,
                    uri = "/mottaker/$sakId/REVURDERING/$referanseId?brevtype=VEDTAK",
                    roller = listOf(rolle),
                ).status shouldBe HttpStatusCode.NotFound
            }
        }

        verify(mottakerService, times(2)).hentMottaker(any(), any(), anyOrNull())
    }

    @Test
    fun `get avviser roller som ikke er saksbehandler eller attestant`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerService = mock<MottakerService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            Brukerrolle.entries
                .filterNot { it == Brukerrolle.Saksbehandler || it == Brukerrolle.Attestant }
                .forEach { rolle ->
                    defaultRequest(
                        method = HttpMethod.Get,
                        uri = "/mottaker/$sakId/REVURDERING/$referanseId?brevtype=VEDTAK",
                        roller = listOf(rolle),
                    ).status shouldBe HttpStatusCode.Forbidden
                }
        }

        verifyNoInteractions(mottakerService)
    }

    @Test
    fun `get feiler nar brevtype er annet`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerService = mock<MottakerService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Get,
                uri = "/mottaker/$sakId/REVURDERING/$referanseId?brevtype=ANNET",
                roller = listOf(Brukerrolle.Saksbehandler),
            ).status shouldBe HttpStatusCode.BadRequest
        }

        verifyNoInteractions(mottakerService)
    }

    @Test
    fun `get feiler nar brevtype mangler`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerService = mock<MottakerService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Get,
                uri = "/mottaker/$sakId/REVURDERING/$referanseId",
                roller = listOf(Brukerrolle.Saksbehandler),
            ).status shouldBe HttpStatusCode.BadRequest
        }

        verifyNoInteractions(mottakerService)
    }

    @Test
    fun `lagre krever saksbehandler`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val request = lagreMottakerRequest(referanseId = referanseId)
        val mottakerService = mock<MottakerService> {
            on { lagreMottaker(any(), any()) } doReturn
                MottakerFnrDomain(
                    navn = request.navn,
                    foedselsnummer = Fnr(request.foedselsnummer!!),
                    adresse = Distribueringsadresse(
                        adresselinje1 = request.adresse.adresselinje1,
                        adresselinje2 = request.adresse.adresselinje2,
                        adresselinje3 = request.adresse.adresselinje3,
                        postnummer = request.adresse.postnummer!!,
                        poststed = request.adresse.poststed!!,
                    ),
                    sakId = sakId,
                    referanseId = referanseId,
                    referanseType = ReferanseTypeMottaker.REVURDERING,
                    brevtype = Brevtype.VEDTAK,
                ).right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/mottaker/$sakId/lagre",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.status shouldBe HttpStatusCode.Created

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/mottaker/$sakId/lagre",
                roller = listOf(Brukerrolle.Attestant),
            ) {
                setBody(serialize(request))
            }.status shouldBe HttpStatusCode.Forbidden
        }

        verify(mottakerService, times(1)).lagreMottaker(any(), any())
    }

    @Test
    fun `lagre feiler nar brevtype mangler`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerService = mock<MottakerService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/mottaker/$sakId/lagre",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    // language=json
                    """
                    {
                      "navn":"Test Testesen",
                      "foedselsnummer":"01010112345",
                      "orgnummer":null,
                      "adresse":{
                        "adresselinje1":"Testveien 1",
                        "adresselinje2":null,
                        "adresselinje3":null,
                        "postnummer":"1234",
                        "poststed":"OSLO"
                      },
                      "referanseId":"$referanseId",
                      "referanseType":"REVURDERING"
                    }
                    """.trimIndent(),
                )
            }.status shouldBe HttpStatusCode.BadRequest
        }

        verifyNoInteractions(mottakerService)
    }

    @Test
    fun `oppdater krever saksbehandler`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val request = oppdaterMottakerRequest(referanseId = referanseId)
        val mottakerService = mock<MottakerService> {
            on { oppdaterMottaker(any(), any()) } doReturn Unit.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Put,
                uri = "/mottaker/$sakId/oppdater",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.status shouldBe HttpStatusCode.OK

            defaultRequest(
                method = HttpMethod.Put,
                uri = "/mottaker/$sakId/oppdater",
                roller = listOf(Brukerrolle.Attestant),
            ) {
                setBody(serialize(request))
            }.status shouldBe HttpStatusCode.Forbidden
        }

        verify(mottakerService, times(1)).oppdaterMottaker(any(), any())
    }

    @Test
    fun `oppdater feiler nar brevtype mangler`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerService = mock<MottakerService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Put,
                uri = "/mottaker/$sakId/oppdater",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    // language=json
                    """
                    {
                      "id":"${UUID.randomUUID()}",
                      "navn":"Test Testesen",
                      "foedselsnummer":"01010112345",
                      "orgnummer":null,
                      "adresse":{
                        "adresselinje1":"Testveien 1",
                        "adresselinje2":null,
                        "adresselinje3":null,
                        "postnummer":"1234",
                        "poststed":"OSLO"
                      },
                      "referanseId":"$referanseId",
                      "referanseType":"REVURDERING"
                    }
                    """.trimIndent(),
                )
            }.status shouldBe HttpStatusCode.BadRequest
        }

        verifyNoInteractions(mottakerService)
    }

    @Test
    fun `slett krever saksbehandler`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val request = MottakerIdentifikator(
            referanseType = ReferanseTypeMottaker.REVURDERING,
            referanseId = referanseId,
            brevtype = Brevtype.VEDTAK,
        )
        val mottakerService = mock<MottakerService> {
            on { slettMottaker(any(), any()) } doReturn Unit.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/mottaker/$sakId/slett",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.status shouldBe HttpStatusCode.NoContent

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/mottaker/$sakId/slett",
                roller = listOf(Brukerrolle.Attestant),
            ) {
                setBody(serialize(request))
            }.status shouldBe HttpStatusCode.Forbidden
        }

        verify(mottakerService, times(1)).slettMottaker(any(), any())
    }

    @Test
    fun `slett feiler nar brevtype mangler`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val mottakerService = mock<MottakerService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services().copy(mottakerService = mottakerService),
                )
            }

            defaultRequest(
                method = HttpMethod.Post,
                uri = "/mottaker/$sakId/slett",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    // language=json
                    """
                    {
                      "referanseType":"REVURDERING",
                      "referanseId":"$referanseId"
                    }
                    """.trimIndent(),
                )
            }.status shouldBe HttpStatusCode.BadRequest
        }

        verifyNoInteractions(mottakerService)
    }

    private fun lagreMottakerRequest(referanseId: UUID): LagreMottaker {
        return LagreMottaker(
            navn = "Test Testesen",
            foedselsnummer = "01010112345",
            orgnummer = null,
            adresse = DistribueringsadresseRequest(
                adresselinje1 = "Testveien 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "1234",
                poststed = "OSLO",
            ),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.name,
            brevtype = Brevtype.VEDTAK.name,
        )
    }

    private fun oppdaterMottakerRequest(referanseId: UUID): OppdaterMottaker {
        return OppdaterMottaker(
            id = UUID.randomUUID().toString(),
            navn = "Test Testesen",
            foedselsnummer = "01010112345",
            orgnummer = null,
            adresse = DistribueringsadresseRequest(
                adresselinje1 = "Testveien 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "1234",
                poststed = "OSLO",
            ),
            referanseId = referanseId.toString(),
            referanseType = ReferanseTypeMottaker.REVURDERING.name,
            brevtype = Brevtype.VEDTAK.name,
        )
    }
}
