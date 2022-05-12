package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.util.UUID

class BehandlingsinformasjonTilVilkårTest {

    @Test
    fun `konverterer flyktning til vilkår`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.Uavklart,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ) shouldBe FlyktningVilkår.IkkeVurdert

        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<FlyktningVilkår.Vurdert>()
            (vilkår as FlyktningVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Innvilget
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    FlyktningGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    FlyktningGrunnlag::id,
                )
            }
        }

        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<FlyktningVilkår.Vurdert>()
            (vilkår as FlyktningVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Avslag
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    FlyktningGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    FlyktningGrunnlag::id,
                )
            }
        }
    }

    @Test
    fun `konverterer lovlig opphold til lovlig vilkår`() {
        Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.Uavklart,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ) shouldBe LovligOppholdVilkår.IkkeVurdert

        Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<LovligOppholdVilkår.Vurdert>()
            (vilkår as LovligOppholdVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Innvilget
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    LovligOppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    LovligOppholdGrunnlag::id,
                )
            }
        }

        Behandlingsinformasjon.LovligOpphold(
            status = Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt,
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<LovligOppholdVilkår.Vurdert>()
            (vilkår as LovligOppholdVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Avslag
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    LovligOppholdGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    LovligOppholdGrunnlag::id,
                )
            }
        }
    }

    @Test
    fun `konverterer fast opphold til vilkår`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.Uavklart,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ) shouldBe FastOppholdINorgeVilkår.IkkeVurdert

        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<FastOppholdINorgeVilkår.Vurdert>()
            (vilkår as FastOppholdINorgeVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Innvilget
                it.vurderingsperioder.single().begrunnelse shouldBe "jambo"
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    FastOppholdINorgeGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    FastOppholdINorgeGrunnlag::id,
                )
            }
        }

        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<FastOppholdINorgeVilkår.Vurdert>()
            (vilkår as FastOppholdINorgeVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Avslag
                it.vurderingsperioder.single().begrunnelse shouldBe "jambo"
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    FastOppholdINorgeGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    FastOppholdINorgeGrunnlag::id,
                )
            }
        }
    }

    @Test
    fun `konverterer institusjonsopphold til vilkår`() {
        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.Uavklart,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ) shouldBe InstitusjonsoppholdVilkår.IkkeVurdert

        Behandlingsinformasjon.Institusjonsopphold(
            status = Behandlingsinformasjon.Institusjonsopphold.Status.VilkårOppfylt,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<InstitusjonsoppholdVilkår.Vurdert>()
            (vilkår as InstitusjonsoppholdVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Innvilget
                it.vurderingsperioder.single().begrunnelse shouldBe "jambo"
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
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<InstitusjonsoppholdVilkår.Vurdert>()
            (vilkår as InstitusjonsoppholdVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Avslag
                it.vurderingsperioder.single().begrunnelse shouldBe "jambo"
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

    @Test
    fun `konverterer personlig oppmøte til vilkår`() {
        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.Uavklart,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ) shouldBe PersonligOppmøteVilkår.IkkeVurdert

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<PersonligOppmøteVilkår.Vurdert>()
            (vilkår as PersonligOppmøteVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Innvilget
                it.vurderingsperioder.single().begrunnelse shouldBe "jambo"
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    PersonligOppmøteGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    PersonligOppmøteGrunnlag::id,
                )
            }
        }

        Behandlingsinformasjon.PersonligOppmøte(
            status = Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
            begrunnelse = "jambo",
        ).tilVilkår(
            stønadsperiode = stønadsperiode2021,
            clock = fixedClock,
        ).let { vilkår ->
            vilkår shouldBe beOfType<PersonligOppmøteVilkår.Vurdert>()
            (vilkår as PersonligOppmøteVilkår.Vurdert).let {
                it.resultat shouldBe Resultat.Avslag
                it.vurderingsperioder.single().begrunnelse shouldBe "jambo"
                it.grunnlag.single().shouldBeEqualToIgnoringFields(
                    PersonligOppmøteGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                    ),
                    PersonligOppmøteGrunnlag::id,
                )
            }
        }
    }
}
