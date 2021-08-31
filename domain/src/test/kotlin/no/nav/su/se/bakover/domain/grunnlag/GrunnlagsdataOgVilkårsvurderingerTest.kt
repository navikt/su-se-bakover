package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.innvilgetUførevilkårForventetInntekt0
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GrunnlagsdataOgVilkårsvurderingerTest {

    @Test
    fun `grunnlagsdata og vilkårsvurderinger med ulike perioder kaster exception`() {
        shouldThrow<IllegalArgumentException> {
            GrunnlagsdataOgVilkårsvurderinger(
                grunnlagsdata = Grunnlagsdata.tryCreate(
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
                vilkårsvurderinger = Vilkårsvurderinger(
                    uføre = innvilgetUførevilkårForventetInntekt0(
                        periode = Periode.create(
                            1.januar(2021),
                            31.mai(2021),
                        ),
                    ),
                    formue = Vilkår.Formue.IkkeVurdert,
                ),
            )
        }.message shouldBe "Grunnlagsdataperioden (Periode(fraOgMed=2021-01-01, tilOgMed=2021-04-30)) må være lik vilkårsvurderingerperioden (Periode(fraOgMed=2021-01-01, tilOgMed=2021-05-31))"
    }

    @Test
    fun `grunnlagsdata og vilkårsvurderinger med like perioder kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger(
            grunnlagsdata = Grunnlagsdata.tryCreate(
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
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = innvilgetUførevilkårForventetInntekt0(
                    periode = Periode.create(
                        1.januar(2021),
                        31.mai(2021),
                    ),
                ),
                formue = Vilkår.Formue.IkkeVurdert,
            ),
        )
    }

    @Test
    fun `ikke vurdert grunnlagsdata og innvilget vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = innvilgetUførevilkårForventetInntekt0(
                    periode = Periode.create(
                        1.januar(2021),
                        31.mai(2021),
                    ),
                ),
                formue = Vilkår.Formue.IkkeVurdert,
            ),
        )
    }

    @Test
    fun `innvilget grunnlagsdata og ikke vurdert vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger(
            grunnlagsdata = Grunnlagsdata.tryCreate(
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
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )
    }

    @Test
    fun `ikke vurdert grunnlagsdata og ikke vurdert vilkårsvurderinger kaster ikke exception`() {
        GrunnlagsdataOgVilkårsvurderinger(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )
    }
}
