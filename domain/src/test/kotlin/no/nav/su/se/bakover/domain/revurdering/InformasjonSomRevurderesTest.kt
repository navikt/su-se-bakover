package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.steg.Vurderingstatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class InformasjonSomRevurderesTest {
    @Test
    fun `oppretter fra liste med revurderingssteg`() {
        InformasjonSomRevurderes.create(
            listOf(
                Revurderingsteg.Inntekt,
                Revurderingsteg.Uførhet,
            ),
        ).let {
            it[Revurderingsteg.Inntekt] shouldBe Vurderingstatus.IkkeVurdert
            it[Revurderingsteg.Uførhet] shouldBe Vurderingstatus.IkkeVurdert
        }
    }

    @Test
    fun `må minst velge en ting som revurderes - liste`() {
        assertThrows<IllegalArgumentException> {
            InformasjonSomRevurderes.create(
                emptyList(),
            )
        }
        InformasjonSomRevurderes.tryCreate(
            emptyList(),
        ) shouldBe InformasjonSomRevurderes.MåRevurdereMinstEnTing.left()
    }

    @Test
    fun `setter revurderingssteg til vurdert`() {
        InformasjonSomRevurderes.create(
            mapOf(
                Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            ),
        ).markerSomVurdert(Revurderingsteg.Inntekt) shouldBe InformasjonSomRevurderes.create(
            mapOf(
                Revurderingsteg.Inntekt to Vurderingstatus.Vurdert,
                Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            ),
        )
    }

    @Test
    fun `må minst velge en ting som revurderes - map`() {
        assertThrows<IllegalArgumentException> {
            InformasjonSomRevurderes.create(
                emptyMap(),
            )
        }
        InformasjonSomRevurderes.tryCreate(
            emptyMap(),
        ) shouldBe InformasjonSomRevurderes.MåRevurdereMinstEnTing.left()
    }
}
