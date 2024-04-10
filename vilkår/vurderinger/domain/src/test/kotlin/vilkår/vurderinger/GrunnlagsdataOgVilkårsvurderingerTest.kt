package vilkår.vurderinger

import arrow.core.nonEmptyListOf
import arrow.core.right
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nyGrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import org.junit.jupiter.api.Test
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.common.domain.VurdertVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt
import vilkår.vurderinger.domain.Grunnlagsdata
import java.util.UUID

internal class GrunnlagsdataOgVilkårsvurderingerTest {

    @Test
    fun `grunnlagsdata og vilkårsvurderinger med ulike perioder kaster exception`() {
        shouldThrow<IllegalArgumentException> {
            GrunnlagsdataOgVilkårsvurderingerRevurdering(
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
                vilkårsvurderinger = VilkårsvurderingerRevurdering.Uføre(
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
        GrunnlagsdataOgVilkårsvurderingerRevurdering(
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
            vilkårsvurderinger = VilkårsvurderingerRevurdering.Uføre(
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
        GrunnlagsdataOgVilkårsvurderingerRevurdering(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = VilkårsvurderingerRevurdering.Uføre(
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
        GrunnlagsdataOgVilkårsvurderingerRevurdering(
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
        GrunnlagsdataOgVilkårsvurderingerRevurdering(
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

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlagsdataOgVilkårsvurderinger = nyGrunnlagsdataOgVilkårsvurderingerSøknadsbehandling()
        grunnlagsdataOgVilkårsvurderinger.copyWithNewIds().let {
            validerIdEndring(
                it.vilkårsvurderinger.opplysningsplikt as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.opplysningsplikt as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.uføreVilkårKastHvisAlder() as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.uføreVilkårKastHvisAlder() as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.flyktningVilkår().getOrFail() as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.flyktningVilkår().getOrFail() as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.lovligOpphold as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.lovligOpphold as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.fastOpphold as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.fastOpphold as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.institusjonsopphold as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.institusjonsopphold as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.utenlandsopphold as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.utenlandsopphold as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.formue as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.formue as VurdertVilkår,
            )
            validerIdEndring(
                it.vilkårsvurderinger.personligOppmøte as VurdertVilkår,
                grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.personligOppmøte as VurdertVilkår,
            )

            it.grunnlagsdata.bosituasjon.size shouldBe 1
            it.grunnlagsdata.bosituasjon.first().shouldBeEqualToIgnoringFields(
                grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.first(),
                Bosituasjon::id,
            )
            it.grunnlagsdata.fradragsgrunnlag.size shouldBe 1
            it.grunnlagsdata.fradragsgrunnlag.first().shouldBeEqualToIgnoringFields(
                grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag.first(),
                Fradragsgrunnlag::id,
            )

            // TODO - fix this - har manuelt sett gjennom innholdet, og bare id'en er endret (som forventet). Likevel feiler testen.
            it.eksterneGrunnlag.shouldBeEqualToExceptId(grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag)
            (it.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).søkers.id shouldNotBe (grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).søkers.id
        }
    }
}
