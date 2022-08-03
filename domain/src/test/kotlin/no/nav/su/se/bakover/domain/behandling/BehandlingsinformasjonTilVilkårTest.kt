package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.util.UUID

class BehandlingsinformasjonTilVilkårTest {

    @Test
    fun `konverterer institusjonsopphold til vilkår`() {
        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.Uavklart,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ) shouldBe InstitusjonsoppholdVilkår.IkkeVurdert

        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<InstitusjonsoppholdVilkår.Vurdert>()
            (vilkår as InstitusjonsoppholdVilkår.Vurdert).let {
                it.vurdering shouldBe Vurdering.Innvilget
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    InstitusjonsoppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    InstitusjonsoppholdGrunnlag::id,
                )
            }
        }

        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårIkkeOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<InstitusjonsoppholdVilkår.Vurdert>()
            (vilkår as InstitusjonsoppholdVilkår.Vurdert).let {
                it.vurdering shouldBe Vurdering.Avslag
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    InstitusjonsoppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    InstitusjonsoppholdGrunnlag::id,
                )
            }
        }
    }
}
