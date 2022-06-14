package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import org.junit.jupiter.api.Test
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
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
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
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
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
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
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
        ) shouldBe Grunnlagsdata.create(emptyList(), emptyList()).right()
    }

    @Test
    fun `oppdaterer periodene på grunnlagene`() {
        val forrigePeriode = år(2021)
        val oppdatertPeriode = januar(2021)
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
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
        val bosiutasjongrunnlag = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = forrigePeriode,
        )
        val grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = listOf(fradragsgrunnlag),
            bosituasjon = listOf(bosiutasjongrunnlag),
        )

        val actual = grunnlagsdata.oppdaterGrunnlagsperioder(
            oppdatertPeriode = oppdatertPeriode,
        ).orNull()!!

        actual.fradragsgrunnlag.size shouldBe 1
        actual.fradragsgrunnlag.first().periode shouldBe oppdatertPeriode
        actual.bosituasjon.size shouldBe 1
        actual.bosituasjon.first().periode shouldBe oppdatertPeriode
    }
}
