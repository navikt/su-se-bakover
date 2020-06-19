package no.nav.su.se.bakover.routes

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.db.EmbeddedDatabase
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class BehandlingRoutesKtTest : ComponentTest() {

    @Test
    fun `henter en behandling`() {
        val token = jwtStub.createTokenFor()
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            val repo = DatabaseBuilder.fromDatasource(EmbeddedDatabase.database)
            val sak = repo.opprettSak(FnrGenerator.random())

            sak.nySøknad(SøknadInnholdTestdataBuilder.build())
            val behandling = sak.sisteStønadsperiode().nyBehandling()
            val behandlingsId = JSONObject(behandling.toJson()).getLong("id")

            withCorrelationId(Get, "$behandlingPath/$behandlingsId") {
                addHeader(Authorization, "Bearer $token")
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(behandlingsId, JSONObject(response.content).getLong("id"))

            }
        }
    }
}
