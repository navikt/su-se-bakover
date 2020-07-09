package no.nav.su.se.bakover.web.routes.behandling

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.sak.sakPath
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
                    id = behandlingsId.toString(),
                    vilkårsvurderinger = vilkårsvurderinger.map { it.toDto() }.toJson()
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
            val sak = repo.opprettSak(FnrGenerator.random())
            val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())

            defaultRequest(HttpMethod.Post, "$sakPath/${sak.toDto().id}/behandlinger") {
                setBody("""{ "soknadId": "${søknad.toDto().id}" }""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                shouldNotThrow<Throwable> { objectMapper.readValue<BehandlingJson>(response.content!!) }
            }
        }
    }

    private fun setupForBehandling(): Behandling {
        val sak = repo.opprettSak(FnrGenerator.random())
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        return sak.opprettSøknadsbehandling(søknad.toDto().id)
    }
}
