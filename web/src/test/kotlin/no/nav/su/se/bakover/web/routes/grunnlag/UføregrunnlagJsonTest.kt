package no.nav.su.se.bakover.web.routes.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.fixedClock
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class UføregrunnlagJsonTest {

    @Test
    fun `serialiserer og deserialiserer uføregrunnlag`() {
        JSONAssert.assertEquals(expectedUføregrunnlagJson, serialize(uføregrunnlag.toJson()), true)
        deserialize<UføregrunnlagJson>(expectedUføregrunnlagJson) shouldBe uføregrunnlag.toJson()
    }

    companion object {
        internal val uføregrunnlagId = UUID.randomUUID()
        internal val uføregrunnlagOpprettet = Tidspunkt.now(fixedClock)
        internal val bosituasjonId = UUID.randomUUID()
        internal val bosituasjonOpprettet = Tidspunkt.now(fixedClock)
        internal val fnrBosituasjon = FnrGenerator.random()

        //language=JSON
        internal val expectedUføregrunnlagJson = """
            {
              "id": "$uføregrunnlagId",
              "opprettet": "${DateTimeFormatter.ISO_INSTANT.format(uføregrunnlagOpprettet)}",
              "periode": {
                "fraOgMed": "2021-01-01",
                "tilOgMed": "2021-12-31"
              },
              "uføregrad": 50,
              "forventetInntekt": 12000
            }
        """.trimIndent()

        //language=JSON
        internal val expectedBosituasjonJson = """
        {
          "type": "EPS_UFØR_FLYKTNING",
          "fnr" : "$fnrBosituasjon",
          "delerBolig": true,
          "ektemakeEllerSamboerUførFlyktning": true,
          "begrunnelse": null
        }
        """.trimIndent()

        internal val uføregrunnlag = Grunnlag.Uføregrunnlag(
            id = uføregrunnlagId,
            opprettet = uføregrunnlagOpprettet,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 12000,
        )

        internal val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = bosituasjonId,
            opprettet = bosituasjonOpprettet,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            fnr = fnrBosituasjon,
            begrunnelse = null
        )
    }
}
