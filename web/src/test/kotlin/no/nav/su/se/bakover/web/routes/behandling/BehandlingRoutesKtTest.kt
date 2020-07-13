package no.nav.su.se.bakover.web.routes.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.toJson
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.UUID

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class BehandlingRoutesKtTest {

    private val repo = DatabaseBuilder.build(EmbeddedDatabase.instance())

    @Test
    fun `henter en behandling`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val sak = repo.opprettSak(FnrGenerator.random())
            val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
            val behandlingDto = sak.opprettSøknadsbehandling(søknad.toDto().id).toDto()

            val behandlingsId = behandlingDto.id
            val vilkårsvurderinger = repo.hentVilkårsvurderinger(behandlingsId)

            defaultRequest(HttpMethod.Get, "$sakPath/${sak.toDto().id}/behandlinger/$behandlingsId").apply {
                val behandlingJson = objectMapper.readValue<BehandlingJson>(response.content!!)
                behandlingJson shouldBe BehandlingJson(
                    id = behandlingsId.toString(),
                    vilkårsvurderinger = vilkårsvurderinger.map { it.toDto() }.toJson(),
                    søknad = behandlingDto.søknad.toJson()
                )
            }
        }
    }

    @Test
    fun `kan opprette behandling på en sak og søknad`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val ids = setup()
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger") {
                setBody("""{ "soknadId": "${ids.søknadId}" }""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val behandling = objectMapper.readValue<BehandlingJson>(response.content!!)
                behandling.vilkårsvurderinger.vilkårsvurderinger.keys shouldHaveAtLeastSize 1
                behandling.søknad.id shouldBe ids.søknadId.toString()
            }
        }
    }

    @Test
    fun `opprette beregning for behandling`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val ids = setup()
            val fom = LocalDate.of(2020, Month.JANUARY, 1)
            val tom = LocalDate.of(2020, Month.DECEMBER, 31)
            val sats = Sats.HØY

            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${ids.behandlingId}/beregn") {
                setBody(
                    """
                    {
                        "fom":"$fom",
                        "tom":"$tom",
                        "sats":"${sats.name}"
                    }
                """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val beregningJson = deserialize<BeregningJson>(response.content!!)
                beregningJson.fom shouldBe fom.toString()
                beregningJson.tom shouldBe tom.toString()
                beregningJson.sats shouldBe Sats.HØY.name
                beregningJson.månedsberegninger shouldHaveSize 12
            }
        }
    }

    @Test
    fun `beregn error handling`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val ids = setup()
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/blabla/beregn") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "gyldig UUID"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${UUID.randomUUID()}/beregn") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldig body"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${UUID.randomUUID()}/beregn") {
                setBody(
                    """
                    {
                        "fom":"${LocalDate.of(2020, Month.JANUARY, 1)}",
                        "tom":"${LocalDate.of(2020, Month.DECEMBER, 31)}",
                        "sats":"LAV"
                    }
                """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${UUID.randomUUID()}/beregn") {
                setBody(
                    """
                    {
                        "fom":"${LocalDate.of(2020, Month.JANUARY, 16)}",
                        "tom":"${LocalDate.of(2020, Month.DECEMBER, 31)}",
                        "sats":"LAV"
                    }
                """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldige input-parametere"
            }
        }
    }

    data class Ids(
        val sakId: UUID,
        val søknadId: UUID,
        val behandlingId: UUID
    )

    private fun setup(): Ids {
        val sak = repo.opprettSak(FnrGenerator.random())
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        val behandling = sak.opprettSøknadsbehandling(søknad.toDto().id)
        return Ids(
            sak.toDto().id,
            søknad.toDto().id,
            behandling.toDto().id
        )
    }
}
