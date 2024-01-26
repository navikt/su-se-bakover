package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import org.junit.jupiter.api.Test
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import java.util.UUID

internal class GrunnlagsdataOgVilkårsvurderingerTest {

    @Test
    fun `grunnlagsdata og vilkårsvurderinger med ulike perioder kaster exception`() {
        shouldThrow<IllegalArgumentException> {
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = nonEmptyListOf(
                        fradragsgrunnlagArbeidsinntekt1000(
                            periode = Periode.create(
                                1.januar(2021),
                                30.april(2021),
                            ),
                        ),
                    ),
                    bosituasjon = listOf(
                        Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(
                                1.januar(2021),
                                30.april(2021),
                            ),
                        ),
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
                    uføre = innvilgetUførevilkårForventetInntekt0(
                        periode = Periode.create(
                            1.januar(2021),
                            31.mai(2021),
                        ),
                    ),
                    formue = formuevilkårIkkeVurdert(),
                    utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                    opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
                    lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                    flyktning = FlyktningVilkår.IkkeVurdert,
                    fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                    personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                    institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                ),
            )
        }.message shouldBe "Grunnlagsdataperioden (Periode(fraOgMed=2021-01-01, tilOgMed=2021-04-30)) må være lik vilkårsvurderingerperioden (Periode(fraOgMed=2021-01-01, tilOgMed=2021-05-31))"
    }

    @Test
    fun `grunnlagsdata og vilkårsvurderinger med like perioder kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt1000(
                        periode = Periode.create(
                            1.januar(2021),
                            31.mai(2021),
                        ),
                    ),
                ),
                bosituasjon = listOf(
                    Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(
                            1.januar(2021),
                            31.mai(2021),
                        ),
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
                uføre = innvilgetUførevilkårForventetInntekt0(
                    periode = Periode.create(
                        1.januar(2021),
                        31.mai(2021),
                    ),
                ),
                formue = formuevilkårIkkeVurdert(),
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
                lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                flyktning = FlyktningVilkår.IkkeVurdert,
                fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
            ),
        )
    }

    @Test
    fun `ikke vurdert grunnlagsdata og innvilget vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
                uføre = innvilgetUførevilkårForventetInntekt0(
                    periode = Periode.create(
                        1.januar(2021),
                        31.mai(2021),
                    ),
                ),
                formue = formuevilkårIkkeVurdert(),
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
                lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                flyktning = FlyktningVilkår.IkkeVurdert,
                fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
            ),
        )
    }

    @Test
    fun `innvilget grunnlagsdata og ikke vurdert vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt1000(
                        periode = Periode.create(
                            1.januar(2021),
                            31.mai(2021),
                        ),
                    ),
                ),
                bosituasjon = listOf(
                    Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(
                            1.januar(2021),
                            31.mai(2021),
                        ),
                    ),
                ),
            ),
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        )
    }

    @Test
    fun `ikke vurdert grunnlagsdata og ikke vurdert vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
        )
    }

    @Test
    fun `oppdaterGrunnlagsperioder på tomme lister`() {
        val tomGrunnlagsdata = Grunnlagsdata.create(emptyList(), emptyList())

        tomGrunnlagsdata.oppdaterGrunnlagsperioder(
            oppdatertPeriode = januar(2021),
            clock = fixedClock,
        ) shouldBe Grunnlagsdata.create(emptyList(), emptyList()).right()
    }

    @Test
    fun `oppdaterer periodene på grunnlagene`() {
        val forrigePeriode = år(2021)
        val oppdatertPeriode = januar(2021)
        val fradragsgrunnlag = Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kontantstøtte,
                månedsbeløp = 0.0,
                periode = forrigePeriode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
        val bosiutasjongrunnlag = Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = forrigePeriode,
        )
        val grunnlagsdata =
            Grunnlagsdata.create(fradragsgrunnlag = listOf(fradragsgrunnlag), bosituasjon = listOf(bosiutasjongrunnlag))

        val actual = grunnlagsdata.oppdaterGrunnlagsperioder(
            oppdatertPeriode = oppdatertPeriode,
            clock = fixedClock,
        ).getOrFail()

        actual.fradragsgrunnlag.size shouldBe 1
        actual.fradragsgrunnlag.first().periode shouldBe oppdatertPeriode
        actual.bosituasjon.size shouldBe 1
        actual.bosituasjon.first().periode shouldBe oppdatertPeriode
    }
}
