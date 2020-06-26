package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class BehandlingRoutesKtTest {

    @Test
    fun `henter en behandling`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val repo = DatabaseBuilder.build(EmbeddedDatabase.instance())
            val sak = repo.opprettSak(FnrGenerator.random())

            sak.nySøknad(SøknadInnholdTestdataBuilder.build())
            val behandling = sak.sisteStønadsperiode().nyBehandling()
            val behandlingsId = JSONObject(behandling.toJson()).getLong("id")

            defaultRequest(Get, "$behandlingPath/$behandlingsId").apply {
                val json = JSONObject(response.content)
                assertEquals(OK, response.status())
                assertEquals(behandlingsId, json.getLong("id"))
                assertEquals(1, json.getJSONArray("vilkårsvurderinger").count())
                assertEquals(
                    Vilkårsvurdering.Status.IKKE_VURDERT.name,
                    json.getJSONArray("vilkårsvurderinger").getJSONObject(0).getString("status")
                )
            }
        }
    }
}
