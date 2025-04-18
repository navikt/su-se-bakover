package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.steg.Vurderingstatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class InformasjonSomRevurderesTest {
    @Test
    fun `oppretter fra liste med revurderingssteg uten vurderinger`() {
        InformasjonSomRevurderes.opprettUtenVurderinger(
            Sakstype.UFØRE,
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
    fun `oppretter fra map med revurderingssteg og vurderinger`() {
        InformasjonSomRevurderes.opprettMedVurderinger(
            Sakstype.UFØRE,
            mapOf(
                Revurderingsteg.Inntekt to Vurderingstatus.Vurdert,
                Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            ),
        ).let {
            it[Revurderingsteg.Inntekt] shouldBe Vurderingstatus.Vurdert
            it[Revurderingsteg.Uførhet] shouldBe Vurderingstatus.IkkeVurdert
        }
    }

    @Test
    fun `setter revurderingsteg til vurdert`() {
        InformasjonSomRevurderes.opprettUtenVurderinger(
            Sakstype.UFØRE,
            listOf(
                Revurderingsteg.Inntekt,
                Revurderingsteg.Uførhet,
            ),
        ).markerSomVurdert(Revurderingsteg.Inntekt) shouldBe InformasjonSomRevurderes.opprettMedVurderinger(
            Sakstype.UFØRE,
            mapOf(
                Revurderingsteg.Inntekt to Vurderingstatus.Vurdert,
                Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert,
            ),
        )
    }

    @Test
    fun `opprettelse må minst ha et revurderingsteg`() {
        assertThrows<IllegalArgumentException> {
            InformasjonSomRevurderes.opprettUtenVurderinger(
                Sakstype.UFØRE,
                emptyList(),
            )
        }
        InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(
            Sakstype.UFØRE,
            emptyList(),
        ) shouldBe InformasjonSomRevurderes.MåRevurdereMinstEnTing.left()
    }

    @Test
    fun `opprettelse av alder kan ikke ha steg uførhet`() {
        assertThrows<IllegalArgumentException> {
            InformasjonSomRevurderes.opprettUtenVurderinger(
                Sakstype.ALDER,
                listOf(Revurderingsteg.Uførhet),
            )
        }
        InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(
            Sakstype.ALDER,
            listOf(Revurderingsteg.Uførhet),
        ) shouldBe InformasjonSomRevurderes.UførhetErUgyldigForAlder.left()
    }

    @Test
    fun `opprettelse av alder kan ikke ha steg flyktning`() {
        assertThrows<IllegalArgumentException> {
            InformasjonSomRevurderes.opprettUtenVurderinger(
                Sakstype.ALDER,
                listOf(Revurderingsteg.Flyktning),
            )
        }
        InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(
            Sakstype.ALDER,
            listOf(Revurderingsteg.Flyktning),
        ) shouldBe InformasjonSomRevurderes.FlyktningErUgyldigForAlder.left()
    }

    @Test
    fun `opprettelse av uføre kan ikke ha steg familiegjenforening`() {
        assertThrows<IllegalArgumentException> {
            InformasjonSomRevurderes.opprettUtenVurderinger(
                Sakstype.UFØRE,
                listOf(Revurderingsteg.Familiegjenforening),
            )
        }
        InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(
            Sakstype.UFØRE,
            listOf(Revurderingsteg.Familiegjenforening),
        ) shouldBe InformasjonSomRevurderes.FamiliegjenforeningErUgyldigForUføre.left()
    }

    @Test
    fun `opprettelse av uføre kan ikke ha steg pensjon`() {
        assertThrows<IllegalArgumentException> {
            InformasjonSomRevurderes.opprettUtenVurderinger(
                Sakstype.UFØRE,
                listOf(Revurderingsteg.Pensjon),
            )
        }
        InformasjonSomRevurderes.opprettUtenVurderingerMedFeilmelding(
            Sakstype.UFØRE,
            listOf(Revurderingsteg.Pensjon),
        ) shouldBe InformasjonSomRevurderes.PensjonErUgyldigForUføre.left()
    }
}
