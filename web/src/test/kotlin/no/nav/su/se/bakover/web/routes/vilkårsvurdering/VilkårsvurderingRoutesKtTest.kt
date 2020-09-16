package no.nav.su.se.bakover.web.routes.vilkårsvurdering

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.behandlingPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class VilkårsvurderingRoutesKtTest {

    private val repo = DatabaseBuilder.build(EmbeddedDatabase.instance())

    @Test
    fun `oppdater vilkårsvurdering for behandling`() {
        withTestApplication({
            testSusebakover()
        }) {
            val behandling = setupForBehandling()
            val behandlingsId = behandling.id
            val vilkårsvurdering = behandling.vilkårsvurderinger().first { it.vilkår == Vilkår.UFØRHET }
            val oppdatering =
                mapOf(
                    vilkårsvurdering.vilkår.name to VilkårsvurderingData(
                        id = vilkårsvurdering.id.toString(),
                        begrunnelse = "Dette kravet er ok",
                        status = Vilkårsvurdering.Status.OK.name
                    )
                )

            defaultRequest(HttpMethod.Patch, "$behandlingPath/$behandlingsId/vilkarsvurderinger") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(objectMapper.writeValueAsString(oppdatering))
            }
            val oppdatert = repo.hentBehandling(behandlingsId)!!
            assertEquals(behandlingsId, oppdatert.id)
            val uførhetVilkår = oppdatert.vilkårsvurderinger().first { it.vilkår == Vilkår.UFØRHET }
            assertEquals(Vilkårsvurdering.Status.OK, uførhetVilkår.status())
            assertEquals("Dette kravet er ok", uførhetVilkår.begrunnelse())
        }
    }

    private fun setupForBehandling(): Behandling {
        val sak = repo.opprettSak(FnrGenerator.random())
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        return sak.opprettSøknadsbehandling(søknad.id)
    }
}
