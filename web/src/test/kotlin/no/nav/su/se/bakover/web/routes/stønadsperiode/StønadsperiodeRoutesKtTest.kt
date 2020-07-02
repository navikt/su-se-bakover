package no.nav.su.se.bakover.web.routes.stønadsperiode

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.collections.shouldHaveSize
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
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
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
                val stønadsperiode = setupForStønadsperiode().toDto()

                defaultRequest(HttpMethod.Post, "$stønadsperiodePath/${stønadsperiode.id}/behandlinger").also {
                    assertEquals(HttpStatusCode.Created, it.response.status())
                }
                repo.hentBehandlinger(stønadsperiode.id) shouldHaveSize 1
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
            val stønadsperiodeId = stønadsperiode.toDto().id
            defaultRequest(HttpMethod.Get, "$stønadsperiodePath/$stønadsperiodeId").also {
                val stønadsperiodeJson = objectMapper.readValue<StønadsperiodeJson>(it.response.content!!)

                assertEquals(HttpStatusCode.OK, it.response.status())
                assertEquals(stønadsperiodeId, stønadsperiodeJson.id)
                assertNotNull(stønadsperiodeJson.søknad)
                assertTrue(stønadsperiodeJson.behandlinger.isNotEmpty())
            }
        }
    }

    private fun setupForStønadsperiode(): Stønadsperiode {
        val sak = repo.opprettSak(FnrGenerator.random())
        sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        return sak.sisteStønadsperiode()
    }
}
