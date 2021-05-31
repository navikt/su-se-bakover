package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class VedtakPåTidslinjeTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 100,
        )

        val vurderingsperiode = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = uføregrunnlag,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = "hei",
        )

        val f1 = FradragFactory.ny(
            type = Fradragstype.Kontantstøtte,
            månedsbeløp = 5000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 28.februar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        val f2 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.oktober(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val f3 = FradragFactory.ny(
            type = Fradragstype.ForventetInntekt,
            månedsbeløp = 1000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val original = Vedtak.VedtakPåTidslinje(
            vedtakId = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(uføregrunnlag),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        vurderingsperiode,
                    ),
                ),
            ),
            fradrag = listOf(f1, f2, f3),
        )
        original.copy(CopyArgs.Tidslinje.Full).let { vedtakPåTidslinje ->
            vedtakPåTidslinje.vedtakId shouldBe original.vedtakId
            vedtakPåTidslinje.opprettet shouldBe original.opprettet
            vedtakPåTidslinje.periode shouldBe original.periode
            vedtakPåTidslinje.grunnlagsdata.uføregrunnlag[0].let {
                it.id shouldNotBe uføregrunnlag.id
                it.periode shouldBe uføregrunnlag.periode
                it.uføregrad shouldBe uføregrunnlag.uføregrad
                it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
            }
            (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Vurdert.Uførhet).let { vilkårcopy ->
                vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                    vurderingsperiodecopy.id shouldNotBe vurderingsperiode.id
                    vurderingsperiodecopy.begrunnelse shouldBe vurderingsperiode.begrunnelse
                    vurderingsperiodecopy.resultat shouldBe vurderingsperiode.resultat
                    vurderingsperiodecopy.periode shouldBe vurderingsperiode.periode
                    vurderingsperiodecopy.grunnlag!!.let {
                        it.id shouldNotBe uføregrunnlag.id
                        it.periode shouldBe uføregrunnlag.periode
                        it.uføregrad shouldBe uføregrunnlag.uføregrad
                        it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                    }
                }
            }
            vedtakPåTidslinje.fradrag shouldBe listOf(f1, f2)
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 100,
        )

        val vurderingsperiode = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = uføregrunnlag,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = "hei",
        )

        val f1 = FradragFactory.ny(
            type = Fradragstype.Kontantstøtte,
            månedsbeløp = 5000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 28.februar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val f2 = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.oktober(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val f3 = FradragFactory.ny(
            type = Fradragstype.ForventetInntekt,
            månedsbeløp = 1000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val original = Vedtak.VedtakPåTidslinje(
            vedtakId = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(uføregrunnlag),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Vurdert.Uførhet.create(
                    vurderingsperioder = nonEmptyListOf(
                        vurderingsperiode,
                    ),
                ),
            ),
            fradrag = listOf(f1, f2, f3),
        )

        original.copy(CopyArgs.Tidslinje.NyPeriode(Periode.create(1.mai(2021), 31.juli(2021)))).let { vedtakPåTidslinje ->
            vedtakPåTidslinje.vedtakId shouldBe original.vedtakId
            vedtakPåTidslinje.opprettet shouldBe original.opprettet
            vedtakPåTidslinje.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
            vedtakPåTidslinje.grunnlagsdata.uføregrunnlag[0].let {
                it.id shouldNotBe uføregrunnlag.id
                it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                it.uføregrad shouldBe uføregrunnlag.uføregrad
                it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
            }
            (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Vurdert.Uførhet).let { vilkårcopy ->
                vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                    vurderingsperiodecopy.id shouldNotBe vurderingsperiode.id
                    vurderingsperiodecopy.begrunnelse shouldBe vurderingsperiode.begrunnelse
                    vurderingsperiodecopy.resultat shouldBe vurderingsperiode.resultat
                    vurderingsperiodecopy.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                    vurderingsperiodecopy.grunnlag!!.let {
                        it.id shouldNotBe uføregrunnlag.id
                        it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                        it.uføregrad shouldBe uføregrunnlag.uføregrad
                        it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                    }
                }
            }
            vedtakPåTidslinje.fradrag.let { fradragCopy ->
                fradragCopy shouldHaveSize 1
                fradragCopy[0].let {
                    it.fradragstype shouldBe Fradragstype.Arbeidsinntekt
                    it.månedsbeløp shouldBe 1000.0
                    it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                    it.utenlandskInntekt shouldBe null
                    it.tilhører shouldBe FradragTilhører.BRUKER
                }
            }
        }
    }
}
