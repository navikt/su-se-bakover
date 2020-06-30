package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpMethod
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class VilkårsvurderingRoutesKtTest {

    private val repo = DatabaseBuilder.build(EmbeddedDatabase.instance())

    @Test
    fun `oppdater vilkårsvurdering for behandling`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            val behandlingJson = JSONObject(setupForBehandling().toJson())
            val behandlingsId = behandlingJson.getLong("id")
            val oppdatering = behandlingJson.getJSONArray("vilkårsvurderinger").getJSONObject(0).apply {
                this.put("status", Vilkårsvurdering.Status.OK.name)
                this.put("begrunnelse", "Dette kravet er ok")
            }.toString()
            defaultRequest(HttpMethod.Patch, "$behandlingPath/$behandlingsId/vilkarsvurderinger") {
                setBody("""[$oppdatering]""")
            }
            val oppdatert = JSONObject(repo.hentBehandling(behandlingsId)!!.toJson()).getJSONArray("vilkårsvurderinger")
                .getJSONObject(0)
            assertEquals(behandlingsId, oppdatert.getLong("id"))
            assertEquals(Vilkårsvurdering.Status.OK.name, oppdatert.getString("status"))
            assertEquals("Dette kravet er ok", oppdatert.getString("begrunnelse"))
        }
    }

    private fun setupForBehandling(): Behandling {
        val sak = repo.opprettSak(FnrGenerator.random())
        sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        return sak.sisteStønadsperiode().nyBehandling()
    }
}
