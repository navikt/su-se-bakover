package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BosituasjonJsonTest {

    @Test
    fun `serialiserer bosituasjon`() {
        JSONAssert.assertEquals(
            expectedBosituasjonJson,
            serialize(listOf(bosituasjon).toJson()),
            true,
        )
    }

    companion object {
        private val bosituasjonId = UUID.randomUUID()
        private val bosituasjonOpprettet = fixedTidspunkt
        private val fnrBosituasjon = Fnr.generer()

        //language=JSON
        internal val expectedBosituasjonJson = """[
        {
          "type": "EPS_UFØR_FLYKTNING",
          "fnr" : "$fnrBosituasjon",
          "delerBolig": null,
          "ektemakeEllerSamboerUførFlyktning": true,
          "sats": "ORDINÆR",
          "periode": {
            "fraOgMed": "2021-01-01",
            "tilOgMed": "2021-12-31"
          }
        }
        ]
        """.trimIndent()

        internal val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = bosituasjonId,
            opprettet = bosituasjonOpprettet,
            periode = år(2021),
            fnr = fnrBosituasjon,
        )
    }
}
