package no.nav.su.se.bakover.web.routes.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.toJson
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test

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
            val behandlingDto = setupForBehandling().toDto()
            val behandlingsId = behandlingDto.id
            val vilkårsvurderinger = repo.hentVilkårsvurderinger(behandlingsId)

            defaultRequest(HttpMethod.Get, "$behandlingPath/$behandlingsId").apply {
                val behandlingJson = objectMapper.readValue<BehandlingJson>(response.content!!)
                behandlingJson shouldBe BehandlingJson(
                    id = behandlingsId,
                    vilkårsvurderinger = vilkårsvurderinger.map { it.toDto() }.toJson()
                )
            }
        }
    }

    private fun setupForBehandling(): Behandling {
        val sak = repo.opprettSak(FnrGenerator.random())
        sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        return sak.sisteStønadsperiode().nyBehandling()
    }
}
