package no.nav.su.se.bakover.domain.vilkår

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
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
import java.util.UUID

internal class VilkårsvurderingerTest {

    @Nested
    inner class Søknadsbehandling {

        @Test
        fun `alle vilkår innvilget gir resultat innvilget`() {
            vilkårsvurderingerSøknadsbehandlingInnvilget().let {
                it.vurdering shouldBe Vilkårsvurderingsresultat.Innvilget(
                    setOf(
                        it.uføre,
                        it.formue,
                        it.flyktning,
                        it.lovligOpphold,
                        it.fastOpphold,
                        it.institusjonsopphold,
                        it.utenlandsopphold,
                        it.personligOppmøte,
                        it.opplysningsplikt,
                        it.personligOppmøte,
                    ),
                )
            }
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
                            grunnlag = Grunnlag.Uføregrunnlag(
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
                (vilkårsvurdering.vurdering as Vilkårsvurderingsresultat.Avslag).let {
                    it.vilkår shouldBe setOf(vilkårsvurdering.uføre)
                    it.avslagsgrunner shouldBe listOf(Avslagsgrunn.UFØRHET)
                    it.tidligsteDatoForAvslag shouldBe 1.september(2021)
                }
            }
        }

        @Test
        fun `alle vilkår avslått gir avslag`() {
            vilkårsvurderingerAvslåttAlle()
                .let { vilkårsvurdering ->
                    (vilkårsvurdering.vurdering as Vilkårsvurderingsresultat.Avslag).let {
                        it.vilkår shouldBe vilkårsvurdering.vilkår
                        it.avslagsgrunner shouldBe listOf(
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
                        it.tidligsteDatoForAvslag shouldBe 1.januar(2021)
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            vilkårsvurderingSøknadsbehandlingIkkeVurdert()
                .let {
                    it.vurdering shouldBe Vilkårsvurderingsresultat.Uavklart(it.vilkår)
                }
        }

        @Test
        fun `ingen vurderingsperioder gir uavklart vilkår`() {
            vilkårsvurderingSøknadsbehandlingIkkeVurdert().vurdering shouldBe Vilkårsvurderingsresultat.Uavklart(
                setOf(
                    UføreVilkår.IkkeVurdert,
                    formuevilkårIkkeVurdert(),
                    FlyktningVilkår.IkkeVurdert,
                    LovligOppholdVilkår.IkkeVurdert,
                    FastOppholdINorgeVilkår.IkkeVurdert,
                    InstitusjonsoppholdVilkår.IkkeVurdert,
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    PersonligOppmøteVilkår.IkkeVurdert,
                    OpplysningspliktVilkår.IkkeVurdert,
                ),
            )
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

            val uavklartMedUføre = uavklart.leggTil(innvilget.uføre)

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

            val uavklartUtenUføreIgjen = uavklartMedUføre.leggTil(uavklart.uføre)

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
    }

    @Nested
    inner class Revurdering {

        @Test
        fun `alle vilkår innvilget gir resultat innvilget`() {
            vilkårsvurderingerRevurderingInnvilget().let {
                it.vurdering shouldBe Vilkårsvurderingsresultat.Innvilget(
                    setOf(
                        it.uføre,
                        it.formue,
                        it.utenlandsopphold,
                        it.opplysningsplikt,
                        it.lovligOpphold,
                        it.flyktning,
                        it.fastOpphold,
                        it.personligOppmøte,
                        it.institusjonsopphold,
                    ),
                )
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
                            grunnlag = Grunnlag.Uføregrunnlag(
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
                (vilkårsvurdering.vurdering as Vilkårsvurderingsresultat.Avslag).let {
                    it.vilkår shouldBe setOf(vilkårsvurdering.uføre)
                    it.avslagsgrunner shouldBe listOf(Avslagsgrunn.UFØRHET)
                    it.tidligsteDatoForAvslag shouldBe 1.september(2021)
                }
            }
        }

        @Test
        fun `alle vilkår avslått gir avslag`() {
            vilkårsvurderingerAvslåttAlleRevurdering()
                .let { vilkårsvurdering ->
                    (vilkårsvurdering.vurdering as Vilkårsvurderingsresultat.Avslag).let {
                        it.vilkår shouldBe vilkårsvurdering.vilkår
                        it.avslagsgrunner shouldBe listOf(
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
                        it.tidligsteDatoForAvslag shouldBe 1.januar(2021)
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            vilkårsvurderingRevurderingIkkeVurdert()
                .let {
                    it.vurdering shouldBe Vilkårsvurderingsresultat.Uavklart(it.vilkår)
                }
        }

        @Test
        fun `et vilkår uavklart gir uavklart`() {
            vilkårsvurderingerRevurderingInnvilget(
                uføre = UføreVilkår.IkkeVurdert,
            ).let {
                it.vurdering shouldBe Vilkårsvurderingsresultat.Uavklart(
                    setOf(
                        UføreVilkår.IkkeVurdert,
                    ),
                )
            }
        }

        @Test
        fun `ingen vurderingsperioder gir uavklart vilkår`() {
            vilkårsvurderingRevurderingIkkeVurdert().vurdering shouldBe Vilkårsvurderingsresultat.Uavklart(
                setOf(
                    UføreVilkår.IkkeVurdert,
                    formuevilkårIkkeVurdert(),
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    OpplysningspliktVilkår.IkkeVurdert,
                    LovligOppholdVilkår.IkkeVurdert,
                    FlyktningVilkår.IkkeVurdert,
                    FastOppholdINorgeVilkår.IkkeVurdert,
                    PersonligOppmøteVilkår.IkkeVurdert,
                    InstitusjonsoppholdVilkår.IkkeVurdert,
                ),
            )
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
    }
}
