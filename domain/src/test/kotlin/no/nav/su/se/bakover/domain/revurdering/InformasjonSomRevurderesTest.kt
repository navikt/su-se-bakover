package no.nav.su.se.bakover.domain.revurdering

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.objectMapper
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class InformasjonSomRevurderesTest {
    @Test
    fun `seriailisering og deserialisering`() {
        val informasjonSomRevurderes = InformasjonSomRevurderes(
            mapOf(
                Revurderingsteg.Inntekt to Vurderingstatus.Vurdert,
                Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            ),
        )
        val serialized = objectMapper.writeValueAsString(informasjonSomRevurderes).also {
            JSONAssert.assertEquals(
                """
                    {
                        "Inntekt": "Vurdert",
                        "Uførhet": "IkkeVurdert"
                    }
                """.trimIndent(),
                it,
                true,
            )
        }

        objectMapper.readValue<InformasjonSomRevurderes>(serialized) shouldBe informasjonSomRevurderes
    }

    @Test
    fun `setter revurderingssteg til vurdert`() {
        InformasjonSomRevurderes(
            mapOf(
                Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            ),
        ).vurdert(Revurderingsteg.Inntekt) shouldBe InformasjonSomRevurderes(
            mapOf(
                Revurderingsteg.Inntekt to Vurderingstatus.Vurdert,
                Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            ),
        )
    }
}
