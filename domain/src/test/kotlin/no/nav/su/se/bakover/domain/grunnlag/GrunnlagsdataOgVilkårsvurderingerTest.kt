package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.innvilgetUførevilkårForventetInntekt0
import no.nav.su.se.bakover.test.månedsperiodeJanuar2021
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
                            begrunnelse = null,
                        ),
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                    uføre = innvilgetUførevilkårForventetInntekt0(
                        periode = Periode.create(
                            1.januar(2021),
                            31.mai(2021),
                        ),
                    ),
                    formue = Vilkår.Formue.IkkeVurdert,
                    utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
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
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                uføre = innvilgetUførevilkårForventetInntekt0(
                    periode = Periode.create(
                        1.januar(2021),
                        31.mai(2021),
                    ),
                ),
                formue = Vilkår.Formue.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
            ),
        )
    }

    @Test
    fun `ikke vurdert grunnlagsdata og innvilget vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                uføre = innvilgetUførevilkårForventetInntekt0(
                    periode = Periode.create(
                        1.januar(2021),
                        31.mai(2021),
                    ),
                ),
                formue = Vilkår.Formue.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
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
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        )
    }

    @Test
    fun `ikke vurdert grunnlagsdata og ikke vurdert vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
        )
    }

    @Test
    fun `oppdaterGrunnlagsperioder på tomme lister`() {
        val tomGrunnlagsdata = Grunnlagsdata.create(emptyList(), emptyList())

        tomGrunnlagsdata.oppdaterGrunnlagsperioder(
            oppdatertPeriode = månedsperiodeJanuar2021,
        ) shouldBe Grunnlagsdata.create(emptyList(), emptyList()).right()
    }

    @Test
    fun `oppdaterer periodene på grunnlagene`() {
        val forrigePeriode = Periode.create(1.januar(2021), 31.desember(2021))
        val oppdatertPeriode = månedsperiodeJanuar2021
        val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.Kontantstøtte),
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
            begrunnelse = null,
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
