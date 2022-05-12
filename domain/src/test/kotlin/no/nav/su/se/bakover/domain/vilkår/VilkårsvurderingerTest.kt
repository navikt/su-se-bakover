package no.nav.su.se.bakover.domain.vilkår

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withAvslåttFlyktning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTest
import no.nav.su.se.bakover.test.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.getOrFail
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
                it.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
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
                    ),
                )
            }
        }

        @Test
        fun `alle vilkår innvilget bortsett fra en enkelt vurderingsperiode gir avslag`() {
            vilkårsvurderingerSøknadsbehandlingInnvilget(
                uføre = Vilkår.Uførhet.Vurdert.tryCreate(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = Grunnlag.Uføregrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.januar(2021), 31.august(2021)),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = 50_000,
                            ),
                            vurderingsperiode = Periode.create(1.januar(2021), 31.august(2021)),
                        ).getOrFail(),
                        Vurderingsperiode.Uføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            vurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
                        ).getOrFail(),
                    ),
                ).getOrFail(),
            ).let { vilkårsvurdering ->
                (vilkårsvurdering.resultat as Vilkårsvurderingsresultat.Avslag).let {
                    it.vilkår shouldBe setOf(vilkårsvurdering.uføre)
                    it.avslagsgrunner shouldBe listOf(Avslagsgrunn.UFØRHET)
                    it.dato shouldBe 1.september(2021)
                }
            }
        }

        @Test
        fun `alle vilkår avslått gir avslag`() {
            vilkårsvurderingerAvslåttAlle()
                .let { vilkårsvurdering ->
                    (vilkårsvurdering.resultat as Vilkårsvurderingsresultat.Avslag).let {
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
                            Avslagsgrunn.MANGLENDE_DOKUMENTASJON
                        )
                        it.dato shouldBe 1.januar(2021)
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            vilkårsvurderingSøknadsbehandlingIkkeVurdert()
                .let {
                    it.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(it.vilkår)
                }
        }

        @Test
        fun `et vilkår uavklart gir uavklart`() {
            vilkårsvurderingerSøknadsbehandlingInnvilget(
                behandlingsinformasjon = Behandlingsinformasjon().withAlleVilkårOppfylt().patch(
                    Behandlingsinformasjon(
                        lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                            status = Behandlingsinformasjon.LovligOpphold.Status.Uavklart, begrunnelse = "",
                        ),
                    ),
                ),
            ).let {
                it.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
                    setOf(
                        LovligOppholdVilkår.IkkeVurdert,
                    ),
                )
            }
        }

        @Test
        fun `ingen vurderingsperioder gir uavklart vilkår`() {
            vilkårsvurderingSøknadsbehandlingIkkeVurdert().resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
                setOf(
                    Vilkår.Uførhet.IkkeVurdert,
                    formuevilkårIkkeVurdert(),
                    FlyktningVilkår.IkkeVurdert,
                    LovligOppholdVilkår.IkkeVurdert,
                    FastOppholdINorgeVilkår.IkkeVurdert,
                    InstitusjonsoppholdVilkår.IkkeVurdert,
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    PersonligOppmøteVilkår.IkkeVurdert,
                    OpplysningspliktVilkår.IkkeVurdert
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
                    it.oppdaterStønadsperiode(Stønadsperiode.create(ny), formuegrenserFactoryTest).periode shouldBe ny
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
        fun `oppdaterer vilkårsvurderinger med informasjon fra behandlingsinformasjon`() {
            val innvilget = vilkårsvurderingerSøknadsbehandlingInnvilget()
            innvilget.resultat shouldBe beOfType<Vilkårsvurderingsresultat.Innvilget>()

            innvilget.oppdater(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = emptyList(),
                    bosituasjon = listOf(bosituasjongrunnlagEnslig(periode = år(2021))),
                ),
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTest,
            ).let {
                it.resultat shouldBe Vilkårsvurderingsresultat.Avslag(
                    vilkår = setOf(it.flyktning),
                )
            }
        }

        @Test
        fun `legg til erstatter eksisternde vilkår med nytt`() {
            val innvilget = vilkårsvurderingerSøknadsbehandlingInnvilget()
            val uavklart = vilkårsvurderingSøknadsbehandlingIkkeVurdert()

            uavklart.vilkår shouldBe setOf(
                Vilkår.Uførhet.IkkeVurdert,
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
                OpplysningspliktVilkår.IkkeVurdert
            )

            val uavklartUtenUføreIgjen = uavklartMedUføre.leggTil(uavklart.uføre)

            uavklartUtenUføreIgjen.vilkår shouldBe setOf(
                Vilkår.Uførhet.IkkeVurdert,
                formuevilkårIkkeVurdert(),
                FlyktningVilkår.IkkeVurdert,
                LovligOppholdVilkår.IkkeVurdert,
                FastOppholdINorgeVilkår.IkkeVurdert,
                InstitusjonsoppholdVilkår.IkkeVurdert,
                UtenlandsoppholdVilkår.IkkeVurdert,
                PersonligOppmøteVilkår.IkkeVurdert,
                OpplysningspliktVilkår.IkkeVurdert
            )
        }
    }

    @Nested
    inner class Revurdering {

        @Test
        fun `alle vilkår innvilget gir resultat innvilget`() {
            vilkårsvurderingerRevurderingInnvilget().let {
                it.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
                    setOf(
                        it.uføre,
                        it.formue,
                        it.utenlandsopphold,
                        it.opplysningsplikt,
                    ),
                )
            }
        }

        @Test
        fun `alle vilkår innvilget bortsett fra en enkelt vurderingsperiode gir avslag`() {
            vilkårsvurderingerRevurderingInnvilget(
                uføre = Vilkår.Uførhet.Vurdert.tryCreate(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = Grunnlag.Uføregrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.januar(2021), 31.august(2021)),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = 50_000,
                            ),
                            vurderingsperiode = Periode.create(1.januar(2021), 31.august(2021)),
                        ).getOrFail(),
                        Vurderingsperiode.Uføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            vurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
                        ).getOrFail(),
                    ),
                ).getOrFail(),
            ).let { vilkårsvurdering ->
                (vilkårsvurdering.resultat as Vilkårsvurderingsresultat.Avslag).let {
                    it.vilkår shouldBe setOf(vilkårsvurdering.uføre)
                    it.avslagsgrunner shouldBe listOf(Avslagsgrunn.UFØRHET)
                    it.dato shouldBe 1.september(2021)
                }
            }
        }

        @Test
        fun `alle vilkår avslått gir avslag`() {
            vilkårsvurderingerAvslåttAlleRevurdering()
                .let { vilkårsvurdering ->
                    (vilkårsvurdering.resultat as Vilkårsvurderingsresultat.Avslag).let {
                        it.vilkår shouldBe vilkårsvurdering.vilkår
                        it.avslagsgrunner shouldBe listOf(
                            Avslagsgrunn.UFØRHET,
                            Avslagsgrunn.FORMUE,
                            Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER,
                            Avslagsgrunn.MANGLENDE_DOKUMENTASJON
                        )
                        it.dato shouldBe 1.januar(2021)
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            vilkårsvurderingRevurderingIkkeVurdert()
                .let {
                    it.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(it.vilkår)
                }
        }

        @Test
        fun `et vilkår uavklart gir uavklart`() {
            vilkårsvurderingerRevurderingInnvilget(
                uføre = Vilkår.Uførhet.IkkeVurdert,
            ).let {
                it.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
                    setOf(
                        Vilkår.Uførhet.IkkeVurdert,
                    ),
                )
            }
        }

        @Test
        fun `ingen vurderingsperioder gir uavklart vilkår`() {
            vilkårsvurderingRevurderingIkkeVurdert().resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
                setOf(
                    Vilkår.Uførhet.IkkeVurdert,
                    formuevilkårIkkeVurdert(),
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    OpplysningspliktVilkår.IkkeVurdert,
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
                    it.oppdaterStønadsperiode(Stønadsperiode.create(ny), formuegrenserFactoryTest).periode shouldBe ny
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
