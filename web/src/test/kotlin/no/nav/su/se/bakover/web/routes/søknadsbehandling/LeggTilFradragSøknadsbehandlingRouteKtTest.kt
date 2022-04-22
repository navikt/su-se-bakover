package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class LeggTilFradragSøknadsbehandlingRouteKtTest {

    @Test
    fun `happy case`() {
        //language=json
        val validBody =
            """
        {
            "fradrag":
                [
                    {
                        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
                        "beløp":9879,
                        "kategori":"Arbeidsinntekt",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"EPS"
                    },
                    {
                        "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                        "beløp":10000,
                        "kategori":"Kontantstøtte",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"BRUKER"
                    }
                ]
        }
            """.trimIndent()

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn behandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${behandling.id}/grunnlag/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
            }
        }
    }

    @Test
    fun `Fradrag uten periode er ikke lov`() {
        //language=json
        val bodyFradragUtenPeriode =
            """
        {
            "fradrag":
                [
                    {
                        "beløp":9879,
                        "kategori":"Arbeidsinntekt",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"EPS"
                    },
                    {
                        "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                        "beløp":10000,
                        "kategori":"Kontantstøtte",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"BRUKER"
                    }
                ]
        }
            """.trimIndent()

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn behandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${behandling.id}/grunnlag/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(bodyFradragUtenPeriode)
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain ("ugyldig_body")
            }
        }
    }

    @Test
    fun `Fradrag med fradragstype som ikke finnes er ikke lov`() {
        //language=json
        val bodyMedUgyldigFradrag =
            """
        {
            "fradrag":
                [
                    {
                        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
                        "beløp":9879,
                        "kategori":"UgyldigFradragstype",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"EPS"
                    },
                    {
                        "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                        "beløp":10000,
                        "kategori":"Kontantstøtte",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"BRUKER"
                    }
                ]
        }
            """.trimIndent()

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn behandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${behandling.id}/grunnlag/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(bodyMedUgyldigFradrag)
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain ("ugyldig_fradragstype")
            }
        }
    }

    @Test
    fun `Fradragstype Forventet Inntekt er ikke lov`() {
        //language=json
        val bodyMedUgyldigFradrag =
            """
        {
            "fradrag":
                [
                    {
                        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
                        "beløp":9879,
                        "kategori":"ForventetInntekt",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"EPS"
                    },
                    {
                        "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                        "beløp":10000,
                        "kategori":"Kontantstøtte",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"BRUKER"
                    }
                ]
        }
            """.trimIndent()

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn behandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${behandling.id}/grunnlag/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(bodyMedUgyldigFradrag)
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain ("ugyldig_fradragstype")
            }
        }
    }

    @Test
    fun `Fradragstype beregnetFradragEPS er ikke lov`() {
        //language=json
        val bodyMedUgyldigFradrag =
            """
        {
            "fradrag":
                [
                    {
                        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
                        "beløp":9879,
                        "kategori":"BeregnetFradragEPS",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"EPS"
                    },
                    {
                        "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                        "beløp":10000,
                        "kategori":"Kontantstøtte",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"BRUKER"
                    }
                ]
        }
            """.trimIndent()

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn behandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${behandling.id}/grunnlag/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(bodyMedUgyldigFradrag)
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain ("ugyldig_fradragstype")
            }
        }
    }

    @Test
    fun `Fradragstype under minsteNivå er ikke lov`() {
        //language=json
        val bodyMedUgyldigFradrag =
            """
        {
            "fradrag":
                [
                    {
                        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
                        "beløp":9879,
                        "kategori":"UnderMinstenivå",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"EPS"
                    },
                    {
                        "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                        "beløp":10000,
                        "kategori":"Kontantstøtte",
                        "spesifisertKategori": null,
                        "utenlandskInntekt":null,
                        "tilhører":"BRUKER"
                    }
                ]
        }
            """.trimIndent()

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn behandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${behandling.id}/grunnlag/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(bodyMedUgyldigFradrag)
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain ("ugyldig_fradragstype")
            }
        }
    }
}
