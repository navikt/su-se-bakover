package no.nav.su.se.bakover.web.routes.dokument

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.service.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class DokumentRoutesKtTest {

    @Test
    fun `sjekker tilganger`() {
        withTestApplication(
            ({ testSusebakover(services = TestServicesBuilder.services()) }),
        ) {
            Brukerrolle.values()
                .filterNot { it == Brukerrolle.Saksbehandler }
                .forEach { rolle ->
                    defaultRequest(
                        Get,
                        "/dokumenter?id=39f05293-39e0-47be-ba35-a7e0b233b630&idType=vedtak",
                        listOf(rolle),
                    ).response.let {
                        it.status() shouldBe HttpStatusCode.Forbidden
                    }
                }
        }
    }

    @Test
    fun `validerer request`() {
        withTestApplication(
            ({ testSusebakover(services = TestServicesBuilder.services()) }),
        ) {
            defaultRequest(
                Get,
                "/dokumenter",
                listOf(Brukerrolle.Saksbehandler),
            ).response.let {
                it.status() shouldBe HttpStatusCode.BadRequest
                it.content shouldContain "Parameter 'id' mangler"
            }

            defaultRequest(
                Get,
                "/dokumenter?id=1231231",
                listOf(Brukerrolle.Saksbehandler),
            ).response.let {
                it.status() shouldBe HttpStatusCode.BadRequest
                it.content shouldContain "Parameter 'idType' mangler"
            }

            defaultRequest(
                Get,
                "/dokumenter?id=1231231&idType=jess",
                listOf(Brukerrolle.Saksbehandler),
            ).response.let {
                it.status() shouldBe HttpStatusCode.BadRequest
                it.content shouldContain "Ugyldig parameter 'id'"
            }

            defaultRequest(
                Get,
                "/dokumenter?id=39f05293-39e0-47be-ba35-a7e0b233b630&idType=jess",
                listOf(Brukerrolle.Saksbehandler),
            ).response.let {
                it.status() shouldBe HttpStatusCode.BadRequest
                it.content shouldContain "Ugyldig parameter 'idType'"
            }
        }
    }

    @Test
    fun `happy case`() {
        val services = TestServicesBuilder.services().copy(
            brev = mock {
                on { hentDokumenterFor(argThat { it is HentDokumenterForIdType.Søknad }) } doReturn listOf(
                    Dokument.UtenMetadata.Informasjon(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.EPOCH,
                        tittel = "en fin tittel",
                        generertDokument = "".toByteArray(),
                        generertDokumentJson = "",
                    ),
                )
            },
        )

        withTestApplication(
            ({ testSusebakover(services = services) }),
        ) {
            defaultRequest(
                method = Get,
                uri = "/dokumenter?id=39f05293-39e0-47be-ba35-a7e0b233b630&idType=søknad",
                roller = listOf(Brukerrolle.Saksbehandler),
            ).response.let {
                it.status() shouldBe HttpStatusCode.OK
                verify(services.brev).hentDokumenterFor(
                    HentDokumenterForIdType.Søknad(
                        UUID.fromString("39f05293-39e0-47be-ba35-a7e0b233b630"),
                    ),
                )
                it.content!!.deserializeList<DokumentJson>().first().let { dokumentJson ->
                    dokumentJson.tittel shouldBe "en fin tittel"
                    dokumentJson.opprettet shouldBe "1970-01-01T00:00:00Z"
                    dokumentJson.dokument contentEquals "".toByteArray()
                }
            }
        }
    }

    @Test
    fun `svarer med 404 hvis ingen dokumenter ble funnet`() {
        val sakId = "39f05293-39e0-47be-ba35-a7e0b233b630"
        val services = TestServicesBuilder.services().copy(
            brev = mock {
                on { hentDokumenterFor(argThat { it is HentDokumenterForIdType.Sak }) } doReturn emptyList()
            },
        )

        withTestApplication(
            ({ testSusebakover(services = services) }),
        ) {
            defaultRequest(
                method = Get,
                uri = "/dokumenter?id=$sakId&idType=sak",
                roller = listOf(Brukerrolle.Saksbehandler),
            ).response.let {
                it.status() shouldBe HttpStatusCode.OK
                it.content shouldBe "[]"
                verify(services.brev).hentDokumenterFor(
                    HentDokumenterForIdType.Sak(
                        UUID.fromString(sakId),
                    ),
                )
            }
        }
    }
}
