package no.nav.su.se.bakover.web.routes.dokument

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.domain.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.domain.dokument.Dokument
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
        testApplication {
            application {
                testSusebakover(services = TestServicesBuilder.services())
            }
            Brukerrolle.values()
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
                testSusebakover(services = TestServicesBuilder.services())
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
        val services = TestServicesBuilder.services(
            brev = mock {
                on { hentDokumenterFor(argThat { it is HentDokumenterForIdType.HentDokumenterForSøknad }) } doReturn listOf(
                    Dokument.UtenMetadata.Informasjon.Annet(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.EPOCH,
                        tittel = "en fin tittel",
                        generertDokument = "".toByteArray(),
                        generertDokumentJson = "",
                    ),
                )
            },
        )

        testApplication {
            application {
                testSusebakover(services = services)
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
                it.bodyAsText().deserializeList<DokumentJson>().first().let { dokumentJson ->
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
        val services = TestServicesBuilder.services(
            brev = mock {
                on { hentDokumenterFor(argThat { it is HentDokumenterForIdType.HentDokumenterForSak }) } doReturn emptyList()
            },
        )

        testApplication {
            application {
                testSusebakover(services = services)
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
}
