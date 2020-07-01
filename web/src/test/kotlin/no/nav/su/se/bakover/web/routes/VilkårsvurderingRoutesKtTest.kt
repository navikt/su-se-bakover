package no.nav.su.se.bakover.web.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
            val behandling = setupForBehandling().toDto()
            val behandlingsId = behandling.id
            val vilkårsvurdering = behandling.vilkårsvurderinger.first()
            val oppdatering = mapOf(
                vilkårsvurdering.vilkår.name to VilkårsvurderingData(
                    id = vilkårsvurdering.id,
                    begrunnelse = "Dette kravet er ok",
                    status = Vilkårsvurdering.Status.OK.name
                )
            )

            defaultRequest(HttpMethod.Patch, "$behandlingPath/$behandlingsId/vilkarsvurderinger") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(mapper.writeValueAsString(oppdatering))
            }
            val oppdatert = repo.hentBehandling(behandlingsId)!!.toDto()
            assertEquals(behandlingsId, oppdatert.id)
            assertEquals(Vilkårsvurdering.Status.OK, oppdatert.vilkårsvurderinger.first().status)
            assertEquals("Dette kravet er ok", oppdatert.vilkårsvurderinger.first().begrunnelse)
        }
    }

    private fun setupForBehandling(): Behandling {
        val sak = repo.opprettSak(FnrGenerator.random())
        sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        return sak.sisteStønadsperiode().nyBehandling()
    }
}
