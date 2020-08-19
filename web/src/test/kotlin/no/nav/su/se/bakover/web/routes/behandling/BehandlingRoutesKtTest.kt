package no.nav.su.se.bakover.web.routes.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.UUID

internal class BehandlingRoutesKtTest {

    private val repo = DatabaseBuilder.build(EmbeddedDatabase.instance())

    @Test
    fun `henter en behandling`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val ids = setup()
            defaultRequest(HttpMethod.Get, "$sakPath/${ids.sakId}/behandlinger/${ids.behandlingId}").apply {
                objectMapper.readValue<BehandlingJson>(response.content!!).let {
                    it.id shouldBe ids.behandlingId
                    it.vilkårsvurderinger.vilkårsvurderinger.keys shouldHaveSize 6
                    it.søknad.id shouldBe ids.søknadId
                    it.beregning!!.id shouldBe ids.beregningId
                }
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
                        "sats":"${sats.name}",
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val behandlingJson = deserialize<BehandlingJson>(response.content!!)
                behandlingJson.beregning!!.fom shouldBe fom.toString()
                behandlingJson.beregning.tom shouldBe tom.toString()
                behandlingJson.beregning.sats shouldBe Sats.HØY.name
                behandlingJson.beregning.månedsberegninger shouldHaveSize 12
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
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${UUID.randomUUID()}/beregn") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldig body"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${UUID.randomUUID()}/beregn") {
                setBody(
                    //language=JSON
                    """
                    {
                        "fom":"${LocalDate.of(2020, Month.JANUARY, 1)}",
                        "tom":"${LocalDate.of(2020, Month.DECEMBER, 31)}",
                        "sats":"LAV",
                        "fradrag":[]
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
                        "sats":"LAV",
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldige input-parametere"
            }
        }
    }

    @Test
    fun `simulering`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val ids = setup()
            defaultRequest(HttpMethod.Post, "$sakPath/missing/behandlinger/${ids.behandlingId}/simuler") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${UUID.randomUUID()}/behandlinger/${ids.behandlingId}/simuler") {}.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke sak"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/blabla/simuler") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${UUID.randomUUID()}/simuler") {}.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Ukjent feil"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${ids.sakId}/behandlinger/${ids.behandlingId}/simuler") {}.apply {
                response.status() shouldBe HttpStatusCode.OK
                response.content shouldContain "oppdrag"
            }
        }
    }

    data class Ids(
        val sakId: String,
        val søknadId: String,
        val behandlingId: String,
        val beregningId: String
    )

    private fun setup(): Ids {
        val sak = repo.opprettSak(FnrGenerator.random())
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        val behandling = sak.opprettSøknadsbehandling(søknad.toDto().id)
        val beregning = behandling.opprettBeregning(
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.LAV
        )
        return Ids(
            sak.toDto().id.toString(),
            søknad.toDto().id.toString(),
            behandling.toDto().id.toString(),
            beregning.toDto().id.toString()
        )
    }
}
