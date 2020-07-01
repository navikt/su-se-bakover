package no.nav.su.se.bakover.web.routes

import io.kotest.assertions.json.shouldMatchJson
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class StønadsperiodeRoutesKtTest {

    private val repo = DatabaseBuilder.build(EmbeddedDatabase.instance())

    @Test
    fun `Opprette en ny behandling i en periode`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            withMigratedDb {
                val stønadsperiodeJson = JSONObject(setupForStønadsperiode().toJson())
                val stønadsperiodeId = stønadsperiodeJson.getLong("id")

                defaultRequest(HttpMethod.Post, "$stønadsperiodePath/$stønadsperiodeId/behandlinger").also {
                    assertEquals(HttpStatusCode.Created, it.response.status())

                    it.response.content!!.shouldMatchJson(
                        //language=JSON
                        """
                       {
                            "id": 1,
                            "vilkårsvurderinger": {
                                "UFØRE":{
                                    "id": 1,
                                    "begrunnelse": "",
                                    "status": "IKKE_VURDERT"
                                }
                            }
                        }
                    """.trimIndent()
                    )
                }
            }
        }
    }

    @Test
    fun `hent stønadsperiode med behandling`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val stønadsperiode = setupForStønadsperiode()
            stønadsperiode.nyBehandling()
            val stønadsperiodeId = JSONObject(stønadsperiode.toJson()).getLong("id")
            defaultRequest(HttpMethod.Get, "$stønadsperiodePath/$stønadsperiodeId").also {
                val responseJson = JSONObject(it.response.content)
                assertEquals(HttpStatusCode.OK, it.response.status())
                assertEquals(stønadsperiodeId, responseJson.getLong("id"))
                assertNotNull(responseJson.getJSONObject("søknad"))
                assertTrue(responseJson.getJSONArray("behandlinger").count() > 0)
            }
        }
    }

    private fun setupForStønadsperiode(): Stønadsperiode {
        val sak = repo.opprettSak(FnrGenerator.random())
        sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        return sak.sisteStønadsperiode()
    }
}
