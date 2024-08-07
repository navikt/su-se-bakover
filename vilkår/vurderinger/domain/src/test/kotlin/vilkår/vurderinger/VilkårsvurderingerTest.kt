package vilkår.vurderinger

import arrow.core.nonEmptyListOf
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttAlle
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttAlleRevurdering
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import vilkår.common.domain.VurdertVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.VurderingsperiodeUføre
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import java.util.UUID

internal class VilkårsvurderingerTest {

    @Nested
    inner class Søknadsbehandling {

        @Test
        fun `alle vilkår innvilget gir resultat innvilget`() {
            vilkårsvurderingerSøknadsbehandlingInnvilget().resultat() shouldBe Vurdering.Innvilget
        }

        @Test
        fun `alle vilkår innvilget bortsett fra en enkelt vurderingsperiode gir avslag`() {
            vilkårsvurderingerSøknadsbehandlingInnvilget(
                uføre = UføreVilkår.Vurdert.tryCreate(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeUføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            vurdering = Vurdering.Innvilget,
                            grunnlag = Uføregrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.januar(2021), 31.august(2021)),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = 50_000,
                            ),
                            vurderingsperiode = Periode.create(1.januar(2021), 31.august(2021)),
                        ).getOrFail(),
                        VurderingsperiodeUføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            vurdering = Vurdering.Avslag,
                            grunnlag = null,
                            vurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
                        ).getOrFail(),
                    ),
                ).getOrFail(),
            ).let { vilkårsvurdering ->
                vilkårsvurdering.resultat() shouldBe Vurdering.Avslag
                vilkårsvurdering.avslagsgrunner shouldBe listOf(Avslagsgrunn.UFØRHET)
            }
        }

        @Test
        fun `alle vilkår avslått gir avslag`() {
            vilkårsvurderingerAvslåttAlle()
                .let { vilkårsvurdering ->
                    vilkårsvurdering.resultat().let {
                        it shouldBe Vurdering.Avslag
                        vilkårsvurdering.avslagsgrunner shouldBe listOf(
                            Avslagsgrunn.UFØRHET,
                            Avslagsgrunn.FORMUE,
                            Avslagsgrunn.FLYKTNING,
                            Avslagsgrunn.OPPHOLDSTILLATELSE,
                            Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE,
                            Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON,
                            Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER,
                            Avslagsgrunn.PERSONLIG_OPPMØTE,
                            Avslagsgrunn.MANGLENDE_DOKUMENTASJON,
                        )
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            vilkårsvurderingSøknadsbehandlingIkkeVurdert()
                .let {
                    it.resultat() shouldBe Vurdering.Uavklart
                    it.avslagsgrunner shouldBe emptyList()
                }
        }

        @Test
        fun `ingen vurderingsperioder gir uavklart vilkår`() {
            vilkårsvurderingSøknadsbehandlingIkkeVurdert().let {
                it.resultat() shouldBe Vurdering.Uavklart
                it.avslagsgrunner shouldBe emptyList()
            }
        }

        @Test
        fun `oppdaterer perioden på alle vilkår`() {
            val gammel = år(2021)
            val ny = Periode.create(1.juli(2021), 31.desember(2021))

            vilkårsvurderingerSøknadsbehandlingInnvilget(periode = gammel)
                .let {
                    it.periode shouldBe gammel
                    it.oppdaterStønadsperiode(
                        Stønadsperiode.create(ny),
                        formuegrenserFactoryTestPåDato(),
                    ).periode shouldBe ny
                }
        }

        @Test
        fun `likhet`() {
            val a = vilkårsvurderingerSøknadsbehandlingInnvilget()
            val b = vilkårsvurderingerSøknadsbehandlingInnvilget()
            a shouldBe b
            (a == b) shouldBe true
            a.erLik(b) shouldBe true

            val c = vilkårsvurderingerAvslåttAlle()
            val d = vilkårsvurderingerAvslåttAlle()
            c shouldBe d
            (c == d) shouldBe true
            c.erLik(d) shouldBe true

            a shouldNotBe c
            (a == c) shouldBe false
            a.erLik(c) shouldBe false
        }

        @Test
        fun `likhet bryr seg bare om den funksjonelle betydningen av verdiene`() {
            val a = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = mai(2021))
            val b = vilkårsvurderingerSøknadsbehandlingInnvilget(periode = juli(2021))

            a shouldBe b
            (a == b) shouldBe true
            a.erLik(b) shouldBe true
        }

        @Test
        fun `legg til erstatter eksisternde vilkår med nytt`() {
            val innvilget = vilkårsvurderingerSøknadsbehandlingInnvilget()
            val uavklart = vilkårsvurderingSøknadsbehandlingIkkeVurdert()

            uavklart.vilkår shouldBe setOf(
                UføreVilkår.IkkeVurdert,
                formuevilkårIkkeVurdert(),
                FlyktningVilkår.IkkeVurdert,
                LovligOppholdVilkår.IkkeVurdert,
                FastOppholdINorgeVilkår.IkkeVurdert,
                InstitusjonsoppholdVilkår.IkkeVurdert,
                UtenlandsoppholdVilkår.IkkeVurdert,
                PersonligOppmøteVilkår.IkkeVurdert,
                OpplysningspliktVilkår.IkkeVurdert,
            )

            val uavklartMedUføre = uavklart.oppdaterVilkår(innvilget.uføre)

            uavklartMedUføre.vilkår shouldBe setOf(
                innvilget.uføre,
                formuevilkårIkkeVurdert(),
                FlyktningVilkår.IkkeVurdert,
                LovligOppholdVilkår.IkkeVurdert,
                FastOppholdINorgeVilkår.IkkeVurdert,
                InstitusjonsoppholdVilkår.IkkeVurdert,
                UtenlandsoppholdVilkår.IkkeVurdert,
                PersonligOppmøteVilkår.IkkeVurdert,
                OpplysningspliktVilkår.IkkeVurdert,
            )

            val uavklartUtenUføreIgjen = uavklartMedUføre.oppdaterVilkår(uavklart.uføre)

            uavklartUtenUføreIgjen.vilkår shouldBe setOf(
                UføreVilkår.IkkeVurdert,
                formuevilkårIkkeVurdert(),
                FlyktningVilkår.IkkeVurdert,
                LovligOppholdVilkår.IkkeVurdert,
                FastOppholdINorgeVilkår.IkkeVurdert,
                InstitusjonsoppholdVilkår.IkkeVurdert,
                UtenlandsoppholdVilkår.IkkeVurdert,
                PersonligOppmøteVilkår.IkkeVurdert,
                OpplysningspliktVilkår.IkkeVurdert,
            )
        }

        @Test
        fun `kopierer innholdet med ny id`() {
            val innvilget = vilkårsvurderingerSøknadsbehandlingInnvilget()

            innvilget.copyWithNewIds().let {
                validerIdEndring(it.opplysningsplikt as VurdertVilkår, innvilget.opplysningsplikt as VurdertVilkår)
                validerIdEndring(it.uføre as VurdertVilkår, innvilget.uføre as VurdertVilkår)
                validerIdEndring(it.flyktning as VurdertVilkår, innvilget.flyktning as VurdertVilkår)
                validerIdEndring(it.lovligOpphold as VurdertVilkår, innvilget.lovligOpphold as VurdertVilkår)
                validerIdEndring(it.fastOpphold as VurdertVilkår, innvilget.fastOpphold as VurdertVilkår)
                validerIdEndring(
                    it.institusjonsopphold as VurdertVilkår,
                    innvilget.institusjonsopphold as VurdertVilkår,
                )
                validerIdEndring(it.utenlandsopphold as VurdertVilkår, innvilget.utenlandsopphold as VurdertVilkår)
                validerIdEndring(it.formue as VurdertVilkår, innvilget.formue as VurdertVilkår)
                validerIdEndring(it.personligOppmøte as VurdertVilkår, innvilget.personligOppmøte as VurdertVilkår)
            }
        }
    }

    @Nested
    inner class Revurdering {

        @Test
        fun `alle vilkår innvilget gir resultat innvilget`() {
            vilkårsvurderingerRevurderingInnvilget().let {
                it.resultat() shouldBe Vurdering.Innvilget
                it.avslagsgrunner shouldBe emptyList()
            }
        }

        @Test
        fun `alle vilkår innvilget bortsett fra en enkelt vurderingsperiode gir avslag`() {
            vilkårsvurderingerRevurderingInnvilget(
                uføre = UføreVilkår.Vurdert.tryCreate(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeUføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            vurdering = Vurdering.Innvilget,
                            grunnlag = Uføregrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.januar(2021), 31.august(2021)),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = 50_000,
                            ),
                            vurderingsperiode = Periode.create(1.januar(2021), 31.august(2021)),
                        ).getOrFail(),
                        VurderingsperiodeUføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            vurdering = Vurdering.Avslag,
                            grunnlag = null,
                            vurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
                        ).getOrFail(),
                    ),
                ).getOrFail(),
            ).let { vilkårsvurdering ->
                (vilkårsvurdering.resultat() as Vurdering.Avslag).let {
                    vilkårsvurdering.avslagsgrunner shouldBe listOf(Avslagsgrunn.UFØRHET)
                }
            }
        }

        @Test
        fun `alle vilkår avslått gir avslag`() {
            vilkårsvurderingerAvslåttAlleRevurdering()
                .let { vilkårsvurdering ->
                    (vilkårsvurdering.resultat() as Vurdering.Avslag).let {
                        vilkårsvurdering.avslagsgrunner shouldBe listOf(
                            Avslagsgrunn.UFØRHET,
                            Avslagsgrunn.FORMUE,
                            Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER,
                            Avslagsgrunn.MANGLENDE_DOKUMENTASJON,
                            Avslagsgrunn.OPPHOLDSTILLATELSE,
                            Avslagsgrunn.FLYKTNING,
                            Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE,
                            Avslagsgrunn.PERSONLIG_OPPMØTE,
                            Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON,
                        )
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            vilkårsvurderingRevurderingIkkeVurdert()
                .let {
                    it.resultat() shouldBe Vurdering.Uavklart
                    it.avslagsgrunner shouldBe emptyList()
                }
        }

        @Test
        fun `et vilkår uavklart gir uavklart`() {
            vilkårsvurderingerRevurderingInnvilget(
                uføre = UføreVilkår.IkkeVurdert,
            ).let {
                it.resultat() shouldBe Vurdering.Uavklart
                it.avslagsgrunner shouldBe emptyList()
            }
        }

        @Test
        fun `ingen vurderingsperioder gir uavklart vilkår`() {
            vilkårsvurderingRevurderingIkkeVurdert().let {
                it.resultat() shouldBe Vurdering.Uavklart
                it.avslagsgrunner shouldBe emptyList()
            }
        }

        @Test
        fun `oppdaterer perioden på alle vilkår`() {
            val gammel = år(2021)
            val ny = Periode.create(1.juli(2021), 31.desember(2021))

            vilkårsvurderingerRevurderingInnvilget(periode = gammel)
                .let {
                    it.periode shouldBe gammel
                    it.oppdaterStønadsperiode(
                        Stønadsperiode.create(ny),
                        formuegrenserFactoryTestPåDato(),
                    ).periode shouldBe ny
                }
        }

        @Test
        fun `periode for ikke vurderte vilkår`() {
            vilkårsvurderingSøknadsbehandlingIkkeVurdert().periode shouldBe null
            vilkårsvurderingRevurderingIkkeVurdert().periode shouldBe null
        }

        @Test
        fun `likhet`() {
            val a = vilkårsvurderingerRevurderingInnvilget()
            val b = vilkårsvurderingerRevurderingInnvilget()
            a shouldBe b
            (a == b) shouldBe true
            a.erLik(b) shouldBe true

            val c = vilkårsvurderingerAvslåttAlleRevurdering()
            val d = vilkårsvurderingerAvslåttAlleRevurdering()
            c shouldBe d
            (c == d) shouldBe true
            c.erLik(d) shouldBe true

            a shouldNotBe c
            (a == c) shouldBe false
            a.erLik(c) shouldBe false
        }

        @Test
        fun `likhet bryr seg bare om den funksjonelle betydningen av verdiene`() {
            val a = vilkårsvurderingerRevurderingInnvilget(periode = mai(2021))
            val b = vilkårsvurderingerRevurderingInnvilget(periode = juli(2021))

            a shouldBe b
            (a == b) shouldBe true
            a.erLik(b) shouldBe true
        }

        @Test
        fun `kopierer innholdet med ny id`() {
            val innvilget = vilkårsvurderingerRevurderingInnvilget()

            innvilget.copyWithNewIds().let {
                validerIdEndring(it.opplysningsplikt as VurdertVilkår, innvilget.opplysningsplikt as VurdertVilkår)
                validerIdEndring(it.uføre as VurdertVilkår, innvilget.uføre as VurdertVilkår)
                validerIdEndring(it.flyktning as VurdertVilkår, innvilget.flyktning as VurdertVilkår)
                validerIdEndring(it.lovligOpphold as VurdertVilkår, innvilget.lovligOpphold as VurdertVilkår)
                validerIdEndring(it.fastOpphold as VurdertVilkår, innvilget.fastOpphold as VurdertVilkår)
                validerIdEndring(
                    it.institusjonsopphold as VurdertVilkår,
                    innvilget.institusjonsopphold as VurdertVilkår,
                )
                validerIdEndring(it.utenlandsopphold as VurdertVilkår, innvilget.utenlandsopphold as VurdertVilkår)
                validerIdEndring(it.formue as VurdertVilkår, innvilget.formue as VurdertVilkår)
                validerIdEndring(it.personligOppmøte as VurdertVilkår, innvilget.personligOppmøte as VurdertVilkår)
            }
        }
    }
}

internal fun validerIdEndring(actual: VurdertVilkår, expected: VurdertVilkår) {
    actual.shouldBeEqualToIgnoringFields(expected, VurdertVilkår::vurderingsperioder, VurdertVilkår::grunnlag)
    actual.vurderingsperioder.size shouldBe 1
    actual.vurderingsperioder.first().shouldBeEqualToIgnoringFields(
        expected.vurderingsperioder.first(),
        Vurderingsperiode::id,
        Vurderingsperiode::grunnlag,
    )
    actual.vurderingsperioder.first().id shouldNotBe expected.vurderingsperioder.first().id
}
