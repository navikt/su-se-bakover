package no.nav.su.se.bakover.web.routes.dokument

import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.HentDokumenterForIdType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.dokumentMedMetadataInformasjonAnnet
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.minimumPdfAzeroPadded
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class DokumentRoutesKtTest {

    @Test
    fun `sjekker tilganger`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            Brukerrolle.entries
                .filterNot { it == Brukerrolle.Saksbehandler }
                .forEach { rolle ->
                    defaultRequest(
                        Get,
                        "/dokumenter?id=39f05293-39e0-47be-ba35-a7e0b233b630&idType=vedtak",
                        listOf(rolle),
                    ).let {
                        it.status shouldBe HttpStatusCode.Forbidden
                    }
                }
        }
    }

    @Test
    fun `validerer request`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            defaultRequest(
                Get,
                "/dokumenter",
                listOf(Brukerrolle.Saksbehandler),
            ).let {
                it.status shouldBe HttpStatusCode.BadRequest
                it.bodyAsText() shouldContain "Parameter 'id' mangler"
            }

            defaultRequest(
                Get,
                "/dokumenter?id=1231231",
                listOf(Brukerrolle.Saksbehandler),
            ).let {
                it.status shouldBe HttpStatusCode.BadRequest
                it.bodyAsText() shouldContain "Parameter 'idType' mangler"
            }

            defaultRequest(
                Get,
                "/dokumenter?id=1231231&idType=jess",
                listOf(Brukerrolle.Saksbehandler),
            ).let {
                it.status shouldBe HttpStatusCode.BadRequest
                it.bodyAsText() shouldContain "Ugyldig parameter 'id'"
            }

            defaultRequest(
                Get,
                "/dokumenter?id=39f05293-39e0-47be-ba35-a7e0b233b630&idType=jess",
                listOf(Brukerrolle.Saksbehandler),
            ).let {
                it.status shouldBe HttpStatusCode.BadRequest
                it.bodyAsText() shouldContain "Ugyldig parameter 'idType'"
            }
        }
    }

    @Test
    fun `happy case`() {
        val dokumentId = UUID.randomUUID()
        val services = TestServicesBuilder.services(
            brev = mock {
                on { hentDokumenterFor(argThat { it is HentDokumenterForIdType.HentDokumenterForSøknad }) } doReturn listOf(
                    Dokument.UtenMetadata.Informasjon.Annet(
                        id = dokumentId,
                        opprettet = Tidspunkt.EPOCH,
                        tittel = "en fin tittel",
                        generertDokument = minimumPdfAzeroPadded(),
                        generertDokumentJson = "",
                    ),
                )
            },
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services)
            }
            defaultRequest(
                method = Get,
                uri = "/dokumenter?id=39f05293-39e0-47be-ba35-a7e0b233b630&idType=søknad",
                roller = listOf(Brukerrolle.Saksbehandler),
            ).let {
                it.status shouldBe HttpStatusCode.OK
                verify(services.brev).hentDokumenterFor(
                    HentDokumenterForIdType.HentDokumenterForSøknad(
                        UUID.fromString("39f05293-39e0-47be-ba35-a7e0b233b630"),
                    ),
                )
                it.bodyAsText().shouldBeSimilarJsonTo(
                    """
                    [
                        {
                            "id": "$dokumentId",
                            "tittel": "en fin tittel",
                            "opprettet": "1970-01-01T00:00:00Z",
                            "dokument": "",
                            "journalført": false,
                            "brevErBestilt": false
                        }
                    ]
                    """.trimIndent(),
                )
            }
        }
    }

    @Test
    fun `svarer med 404 hvis ingen dokumenter ble funnet`() {
        val sakId = "39f05293-39e0-47be-ba35-a7e0b233b630"
        val services = TestServicesBuilder.services(
            brev = mock {
                on { hentDokumenterFor(argThat { it is HentDokumenterForIdType.HentDokumenterForSak }) } doReturn emptyList()
            },
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services)
            }
            defaultRequest(
                method = Get,
                uri = "/dokumenter?id=$sakId&idType=sak",
                roller = listOf(Brukerrolle.Saksbehandler),
            ).let {
                it.status shouldBe HttpStatusCode.OK
                it.bodyAsText() shouldBe "[]"
                verify(services.brev).hentDokumenterFor(
                    HentDokumenterForIdType.HentDokumenterForSak(
                        UUID.fromString(sakId),
                    ),
                )
            }
        }
    }

    @Test
    fun `finner dokument med angitt id`() {
        val id = UUID.randomUUID()
        val dokument = dokumentMedMetadataInformasjonAnnet(id = id)
        val services = TestServicesBuilder.services(
            brev = mock { on { hentDokument(any()) } doReturn dokument.right() },
        )

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = services)
            }
            defaultRequest(
                method = Get,
                uri = "/dokumenter/$id",
                roller = listOf(Brukerrolle.Saksbehandler),
            ).let {
                it.status shouldBe HttpStatusCode.OK
                it.readRawBytes() shouldBe dokument.generertDokument.getContent()
                verify(services.brev).hentDokument(argThat { it shouldBe id })
            }
        }
    }
}
