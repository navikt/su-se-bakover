package no.nav.su.se.bakover.web.routes.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class UføregrunnlagJsonTest {

    @Test
    fun `serialiserer og deserialiserer uføregrunnlag`() {
        JSONAssert.assertEquals(
            expectedUføregrunnlagJson,
            serialize(uføregrunnlag.toJson()),
            true,
        )
        deserialize<UføregrunnlagJson>(expectedUføregrunnlagJson) shouldBe uføregrunnlag.toJson()
    }

    companion object {
        private val uføregrunnlagId = UUID.randomUUID()
        private val uføregrunnlagOpprettet = fixedTidspunkt

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

        internal val uføregrunnlag = Uføregrunnlag(
            id = uføregrunnlagId,
            opprettet = uføregrunnlagOpprettet,
            periode = år(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 12000,
        )
    }
}
