package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.søknad.nySakMedLukketSøknad
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukketJson
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class SøknadRoutesKtTest {

    @Test
    fun `ok lukking av søknad svarer med OK`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        lukkSøknad = mock { on { lukkSøknad(any()) } doReturn nySakMedLukketSøknad().first },
                    ),
                )
            }
            defaultRequest(
                method = Post,
                uri = "soknad/${UUID.randomUUID()}/lukk",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                header(ContentType, Json.toString())
                setBody(
                    serialize(
                        LukketJson.TrukketJson(
                            datoSøkerTrakkSøknad = 1.januar(2020),
                            type = LukketJson.LukketType.TRUKKET,
                        ),
                    ),
                )
            }.apply {
                status shouldBe OK
            }
        }
    }

    @Test
    fun `fritekst er påkrevet for lukking med fritekst`() {
        testApplication {
            application { testSusebakoverWithMockedDb() }
            defaultRequest(
                method = Post,
                uri = "soknad/${UUID.randomUUID()}/lukk",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                header(ContentType, Json.toString())
                setBody(
                    serialize(
                        LukketJson.AvvistJson(
                            type = LukketJson.LukketType.AVVIST,
                            brevConfig = LukketJson.AvvistJson.BrevConfigJson(
                                brevtype = LukketJson.BrevType.FRITEKST,
                                null,
                            ),
                        ),
                    ),
                )
            }.apply {
                status shouldBe BadRequest
            }
        }
    }

    @Test
    fun `ugyldig input gir bad request`() {
        testApplication {
            application { testSusebakoverWithMockedDb() }
            defaultRequest(
                method = Post,
                uri = "soknad/${UUID.randomUUID()}/lukk",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                header(ContentType, Json.toString())
                setBody(
                    """
                        {
                        "type" : "ugyldigtype",
                        "datoSøkerTrakkSøknad" : "2020-01-01"
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe BadRequest
            }
        }
    }

    @Test
    fun `brevutkast svarer med OK og tilhørende pdf`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        lukkSøknad = mock {
                            on { lagBrevutkast(any()) } doReturn Pair(fnr, "".toByteArray())
                        },
                    ),
                )
            }
            defaultRequest(
                method = Post,
                uri = "soknad/${UUID.randomUUID()}/lukk/brevutkast",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    serialize(
                        LukketJson.TrukketJson(
                            datoSøkerTrakkSøknad = 1.januar(2020),
                            type = LukketJson.LukketType.TRUKKET,
                        ),
                    ),
                )
            }.apply {
                status shouldBe OK
                contentType() shouldBe io.ktor.http.ContentType.Application.Pdf
            }
        }
    }

    @Test
    fun `svarer med OK hvis avslått med manglende dokumentasjon`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        avslåSøknadManglendeDokumentasjonService = mock {
                            on { avslå(any()) } doReturn søknadsbehandlingIverksattAvslagUtenBeregning().first.right()
                        },
                    ),
                )
            }
            defaultRequest(
                method = Post,
                uri = "soknad/${UUID.randomUUID()}/avslag",
                roller = listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "fritekst" : "coco jambo"
                    }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe OK
            }
        }
    }
}
