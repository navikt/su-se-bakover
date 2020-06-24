package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.web.ComponentTest
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.susebakover
import no.nav.su.se.bakover.withCorrelationId
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class StønadsperiodeRoutesKtTest : ComponentTest() {

    @Test
    fun `Opprette en ny behandling i en periode`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients())
        }) {
            val repo = DatabaseBuilder.fromDatasource(EmbeddedDatabase.database)
            val sak = repo.opprettSak(FnrGenerator.random())
            sak.nySøknad(SøknadInnholdTestdataBuilder.build())

            val stønadsperiodeId = JSONObject(sak.sisteStønadsperiode().toJson()).getLong("id")

            withCorrelationId(
                    HttpMethod.Post, "$stønadsperiodePath/$stønadsperiodeId/behandlinger"
            ) {
                addHeader(HttpHeaders.Authorization, jwt)
            }.also {
                assertEquals(HttpStatusCode.Created, it.response.status())
            }
        }
    }
}