package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.fixedClock
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BosituasjonJsonTest {

    @Test
    fun `serialiserer bosituasjon`() {
        JSONAssert.assertEquals(expectedBosituasjonJson, serialize(listOf(bosituasjon).toJson()), true)
    }

    companion object {
        private val bosituasjonId = UUID.randomUUID()
        private val bosituasjonOpprettet = Tidspunkt.now(fixedClock)
        private val fnrBosituasjon = FnrGenerator.random()

        //language=JSON
        internal val expectedBosituasjonJson = """[
        {
          "type": "EPS_UFØR_FLYKTNING",
          "fnr" : "$fnrBosituasjon",
          "delerBolig": null,
          "ektemakeEllerSamboerUførFlyktning": true,
          "begrunnelse": null,
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
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            fnr = fnrBosituasjon,
            begrunnelse = null,
        )
    }
}
