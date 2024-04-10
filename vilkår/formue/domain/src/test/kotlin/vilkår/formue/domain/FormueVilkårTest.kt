package vilkår.formue.domain

import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldHaveSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEpsAvslått
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import vilkår.common.domain.slåSammenLikePerioder
import java.util.UUID

internal class FormueVilkårTest {

    @Test
    fun `slår sammen tilstøtende og like formueperioder`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormueVurderingsperiode(periodeInnenfor2021 = februar(2021))
        val f3 = lagFormueVurderingsperiode(
            periodeInnenfor2021 = mars(2021),
            vurdering = Vurdering.Avslag,
            grunnlag = formueGrunnlagUtenEpsAvslått(periode = mars(2021)),
        )

        val actual = nonEmptyListOf(f1, f2, f3).slåSammenLikePerioder()
        actual.size shouldBe 2
        actual.first() shouldBe lagFormueVurderingsperiode(
            id = actual.first().id,
            periodeInnenfor2021 = Periode.create(1.januar(2021), 28.februar(2021)),
            grunnlagsId = actual.first().grunnlag.id,
        )
        actual.last() shouldBe f3.copy(
            id = actual.last().id,
            grunnlag = f3.grunnlag.copy(
                id = actual.last().grunnlag.id,
            ),
        )
    }

    @Test
    fun `2 formue-perioder som tilsøtter og er lik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormueVurderingsperiode(periodeInnenfor2021 = februar(2021))

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `2 formue-perioder som ikke tilsøtter, men er lik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormueVurderingsperiode(periodeInnenfor2021 = mars(2021))

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue-perioder som tilstøter, men resultat er ulik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormueVurderingsperiode(
            periodeInnenfor2021 = februar(2021),
            vurdering = Vurdering.Avslag,
            grunnlag = formueGrunnlagUtenEpsAvslått(periode = februar(2021)),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue-perioder som tilsøtter, men grunnlag er ulik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormueVurderingsperiode(
            periodeInnenfor2021 = februar(2021),
            grunnlag = Formuegrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
                epsFormue = null,
                søkersFormue = Verdier.empty().copy(
                    verdiEiendommer = 100,
                ),
                år(2021),
            ),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue-perioder som tilsøtter, grunnalgs-begrunnelse som er tom eller null behandles som lik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = januar(2021))
        val f2 = lagFormueVurderingsperiode(
            periodeInnenfor2021 = februar(2021),
            grunnlag = Formuegrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
                epsFormue = null,
                søkersFormue = Verdier.empty(),
                år(2021),
            ),
        )

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `fjerner formue for EPS for ønsket periode, og bevarer for resterende perioder`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mai(2021)),
            bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
                periode = Periode.create(1.januar(2021), 31.mai(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.fjernEPSFormue(
                listOf(
                    Periode.create(1.februar(2021), 31.mars(2021)),
                    mai(2021),
                ),
            ).let { nyttVilkår ->
                nyttVilkår.vurderingsperioder shouldHaveSize 4

                nyttVilkår.vurderingsperioder[0].let {
                    it.periode shouldBe januar(2021)
                    it.harEPSFormue() shouldBe true
                }
                nyttVilkår.vurderingsperioder[1].let {
                    it.periode shouldBe Periode.create(1.februar(2021), 31.mars(2021))
                    it.harEPSFormue() shouldBe false
                }
                nyttVilkår.vurderingsperioder[2].let {
                    it.periode shouldBe april(2021)
                    it.harEPSFormue() shouldBe true
                }
                nyttVilkår.vurderingsperioder[3].let {
                    it.periode shouldBe mai(2021)
                    it.harEPSFormue() shouldBe false
                }
            }
        }
    }

    @Test
    fun `fjerner formue for EPS for ønsket periode fullstendig`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
                periode = Periode.create(1.januar(2021), 31.mars(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.fjernEPSFormue(listOf(Periode.create(1.januar(2021), 31.mars(2021)))).let { nyttVilkår ->
                nyttVilkår.vurderingsperioder shouldHaveSize 1

                nyttVilkår.vurderingsperioder[0].let {
                    it.periode shouldBe Periode.create(1.januar(2021), 31.mars(2021))
                    it.harEPSFormue() shouldBe false
                }
            }
        }
    }

    @Test
    fun `fjerning av formue for EPS har ingen effekt hvis det ikke eksisterer EPS`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            bosituasjon = bosituasjongrunnlagEnslig(
                periode = Periode.create(1.januar(2021), 31.mars(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.fjernEPSFormue(listOf(Periode.create(1.januar(2021), 31.mars(2021))))
                .erLik(opprinneligVilkår)
        }
    }

    @Test
    fun `fjerning av EPS uten å spesifisere perioder for fjerning har ingen effekt`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
                periode = Periode.create(1.januar(2021), 31.mars(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.fjernEPSFormue(emptyList()).erLik(opprinneligVilkår)
        }
    }

    @Test
    fun `fjerning av formue for EPS for perioder som ikke overlapper formue`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
                periode = Periode.create(1.januar(2021), 31.mars(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.fjernEPSFormue(listOf(Periode.create(1.februar(2022), 31.juli(2022))))
                .erLik(opprinneligVilkår)
        }
    }

    @Test
    fun `legger til tom formue for EPS for ønsket periode`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            bosituasjon = bosituasjongrunnlagEnslig(
                periode = Periode.create(1.januar(2021), 31.mars(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.harEPSFormue() shouldBe false
            opprinneligVilkår.leggTilTomEPSFormueHvisDetMangler(listOf(februar(2021))).let {
                it.grunnlag shouldHaveSize 3
                it.grunnlag[0].erLik(opprinneligVilkår.grunnlag.single())
                it.grunnlag[0].harEPSFormue() shouldBe false
                !it.grunnlag[1].erLik(opprinneligVilkår.grunnlag.single())
                it.grunnlag[1].harEPSFormue() shouldBe true
                it.grunnlag[2].erLik(opprinneligVilkår.grunnlag.single())
                it.grunnlag[2].harEPSFormue() shouldBe false
            }
        }
    }

    @Test
    fun `legger til tom formue for EPS for ønsket hele perioden`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            bosituasjon = bosituasjongrunnlagEnslig(
                periode = Periode.create(1.januar(2021), 31.mars(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.harEPSFormue() shouldBe false
            opprinneligVilkår.leggTilTomEPSFormueHvisDetMangler(listOf(Periode.create(1.januar(2021), 31.mars(2021))))
                .let {
                    opprinneligVilkår.harEPSFormue() shouldBe false
                    it.grunnlag shouldHaveSize 1
                    !it.grunnlag[0].erLik(opprinneligVilkår.grunnlag.single())
                    it.harEPSFormue() shouldBe true
                }
        }
    }

    @Test
    fun `legg til tom formue har ingen effekt dersom den ikke mangler`() {
        innvilgetFormueVilkår(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
                periode = Periode.create(1.januar(2021), 31.mars(2021)),
            ),
        ).let { opprinneligVilkår ->
            opprinneligVilkår.harEPSFormue() shouldBe true
            opprinneligVilkår.leggTilTomEPSFormueHvisDetMangler(listOf(Periode.create(1.januar(2021), 31.mars(2021))))
                .let {
                    opprinneligVilkår.harEPSFormue() shouldBe true
                    it.erLik(opprinneligVilkår) shouldBe true
                }
        }
    }

    @Test
    fun `kopierer innholdet med ny id`() {
        val vilkår = formuevilkårMedEps0Innvilget()

        vilkår.copyWithNewId().let {
            it.vurderingsperioder.size shouldBe 1
            it.vurderingsperioder.first().id shouldNotBe vilkår.vurderingsperioder.first().id
            it.vurderingsperioder.first().grunnlag.id shouldNotBe vilkår.vurderingsperioder.first().grunnlag.id
        }
    }

    private fun lagFormueVurderingsperiode(
        id: UUID = UUID.randomUUID(),
        tidspunkt: Tidspunkt = fixedTidspunkt,
        vurdering: Vurdering = Vurdering.Innvilget,
        periodeInnenfor2021: Periode,
        grunnlagsId: UUID = id,
        grunnlag: Formuegrunnlag = Formuegrunnlag.create(
            id = grunnlagsId,
            periode = periodeInnenfor2021,
            opprettet = tidspunkt,
            epsFormue = null,
            søkersFormue = Verdier.empty(),
            behandlingsPeriode = år(2021),
        ),
    ): VurderingsperiodeFormue {
        require(år(2021).inneholder(periodeInnenfor2021))
        return VurderingsperiodeFormue.tryCreateFromGrunnlag(
            id = id,
            grunnlag = grunnlag,
            formuegrenserFactory = formuegrenserFactoryTestPåDato(),
        ).also {
            require(it.vurdering == vurdering)
        }
    }
}
