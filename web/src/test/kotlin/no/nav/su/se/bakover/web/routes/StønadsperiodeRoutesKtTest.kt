package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import kotlin.test.assertEquals
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.web.*
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class StønadsperiodeRoutesKtTest {

    @Test
    fun `Opprette en ny behandling i en periode`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val repo = DatabaseBuilder.build(EmbeddedDatabase.instance())
            val sak = repo.opprettSak(FnrGenerator.random())
            sak.nySøknad(SøknadInnholdTestdataBuilder.build())

            val stønadsperiodeId = JSONObject(sak.sisteStønadsperiode().toJson()).getLong("id")

            defaultRequest(HttpMethod.Post, "$stønadsperiodePath/$stønadsperiodeId/behandlinger").also {
                assertEquals(HttpStatusCode.Created, it.response.status())
            }
        }
    }
}
