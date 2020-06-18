package no.nav.su.se.bakover.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.db.EmbeddedDatabase
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class StønadsperiodeRoutesKtTest : ComponentTest() {

    @Test
    fun `Opprette en ny behandling i en periode`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            val repo = DatabaseSøknadRepo(EmbeddedDatabase.database)
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