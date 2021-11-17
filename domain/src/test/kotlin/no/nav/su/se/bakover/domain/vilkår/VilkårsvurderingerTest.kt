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
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeJuli2021
import no.nav.su.se.bakover.test.periodeMai2021
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttAlle
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttAlleRevurdering
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilgetRevurdering
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VilkårsvurderingerTest {

    @Nested
    inner class Søknadsbehandling {

        @Test
        fun `alle vilkår innvilget gir resultat innvilget`() {
            vilkårsvurderingerInnvilget().let {
                it.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
                    setOf(
                        it.uføre,
                        it.formue,
                        it.flyktning,
                        it.lovligOpphold,
                        it.fastOpphold,
                        it.institusjonsopphold,
                        it.oppholdIUtlandet,
                        it.personligOppmøte,
                    ),
                )
            }
        }

        @Test
        fun `alle vilkår innvilget bortsett fra en enkelt vurderingsperiode gir avslag`() {
            vilkårsvurderingerInnvilget(
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
                            begrunnelse = "ja",
                        ).getOrFail(),
                        Vurderingsperiode.Uføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            vurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
                            begrunnelse = "nei",
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
                        )
                        it.dato shouldBe 1.januar(2021)
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert
                .let {
                    it.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(it.vilkår)
                }
        }

        @Test
        fun `et vilkår uavklart gir uavklart`() {
            vilkårsvurderingerInnvilget(
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
            Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
                setOf(
                    Vilkår.Uførhet.IkkeVurdert,
                    Vilkår.Formue.IkkeVurdert,
                    FlyktningVilkår.IkkeVurdert,
                    LovligOppholdVilkår.IkkeVurdert,
                    FastOppholdINorgeVilkår.IkkeVurdert,
                    InstitusjonsoppholdVilkår.IkkeVurdert,
                    OppholdIUtlandetVilkår.IkkeVurdert,
                    PersonligOppmøteVilkår.IkkeVurdert,
                ),
            )
        }

        @Test
        fun `oppdaterer perioden på alle vilkår`() {
            val gammel = Periode.create(1.januar(2021), 31.desember(2021))
            val ny = Periode.create(1.juli(2021), 31.desember(2021))

            vilkårsvurderingerInnvilget(periode = gammel)
                .let {
                    it.periode shouldBe gammel
                    it.oppdaterStønadsperiode(Stønadsperiode.create(ny, "")).periode shouldBe ny
                }
        }

        @Test
        fun `likhet`() {
            val a = vilkårsvurderingerInnvilget()
            val b = vilkårsvurderingerInnvilget()
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
            val a = vilkårsvurderingerInnvilget(periode = periodeMai2021)
            val b = vilkårsvurderingerInnvilget(periode = periodeJuli2021)

            a shouldBe b
            (a == b) shouldBe true
            a.erLik(b) shouldBe true
        }

        @Test
        fun `oppdaterer vilkårsvurderinger med informasjon fra behandlingsinformasjon`() {
            val innvilget = vilkårsvurderingerInnvilget()
            innvilget.resultat shouldBe beOfType<Vilkårsvurderingsresultat.Innvilget>()

            innvilget.oppdater(
                stønadsperiode = Stønadsperiode.create(periode2021, ""),
                behandlingsinformasjon = Behandlingsinformasjon().withAvslåttFlyktning(),
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = emptyList(),
                    bosituasjon = listOf(bosituasjongrunnlagEnslig(periode2021)),
                ),
                clock = fixedClock,
            ).let {
                it.resultat shouldBe Vilkårsvurderingsresultat.Avslag(
                    vilkår = setOf(it.flyktning),
                )
            }
        }

        @Test
        fun `ignorerer uføre, bosituasjon og null fra behandlingsinformasjon ved oppdatering`() {
            val uavklart = Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert

            uavklart.resultat shouldBe beOfType<Vilkårsvurderingsresultat.Uavklart>()

            val behandlingsinformasjon = Behandlingsinformasjon
                .lagTomBehandlingsinformasjon()
                .copy(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                        uføregrad = 20,
                        forventetInntekt = 10,
                        begrunnelse = "",
                    ),
                )

            uavklart.oppdater(
                stønadsperiode = Stønadsperiode.create(periode2021, ""),
                behandlingsinformasjon = behandlingsinformasjon,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                clock = fixedClock,
            ) shouldBe uavklart
        }

        @Test
        fun `legg til erstatter eksisternde vilkår med nytt`() {
            val innvilget = vilkårsvurderingerInnvilget()
            val uavklart = Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert

            uavklart.vilkår shouldBe setOf(
                Vilkår.Uførhet.IkkeVurdert,
                Vilkår.Formue.IkkeVurdert,
                FlyktningVilkår.IkkeVurdert,
                LovligOppholdVilkår.IkkeVurdert,
                FastOppholdINorgeVilkår.IkkeVurdert,
                InstitusjonsoppholdVilkår.IkkeVurdert,
                OppholdIUtlandetVilkår.IkkeVurdert,
                PersonligOppmøteVilkår.IkkeVurdert,
            )

            val uavklartMedUføre = uavklart.leggTil(innvilget.uføre)

            uavklartMedUføre.vilkår shouldBe setOf(
                innvilget.uføre,
                Vilkår.Formue.IkkeVurdert,
                FlyktningVilkår.IkkeVurdert,
                LovligOppholdVilkår.IkkeVurdert,
                FastOppholdINorgeVilkår.IkkeVurdert,
                InstitusjonsoppholdVilkår.IkkeVurdert,
                OppholdIUtlandetVilkår.IkkeVurdert,
                PersonligOppmøteVilkår.IkkeVurdert,
            )

            val uavklartUtenUføreIgjen = uavklartMedUføre.leggTil(uavklart.uføre)

            uavklartUtenUføreIgjen.vilkår shouldBe setOf(
                Vilkår.Uførhet.IkkeVurdert,
                Vilkår.Formue.IkkeVurdert,
                FlyktningVilkår.IkkeVurdert,
                LovligOppholdVilkår.IkkeVurdert,
                FastOppholdINorgeVilkår.IkkeVurdert,
                InstitusjonsoppholdVilkår.IkkeVurdert,
                OppholdIUtlandetVilkår.IkkeVurdert,
                PersonligOppmøteVilkår.IkkeVurdert,
            )
        }
    }

    @Nested
    inner class Revurdering {

        @Test
        fun `alle vilkår innvilget gir resultat innvilget`() {
            vilkårsvurderingerInnvilgetRevurdering().let {
                it.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
                    setOf(
                        it.uføre,
                        it.formue,
                        it.oppholdIUtlandet,
                    ),
                )
            }
        }

        @Test
        fun `alle vilkår innvilget bortsett fra en enkelt vurderingsperiode gir avslag`() {
            vilkårsvurderingerInnvilgetRevurdering(
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
                            begrunnelse = "ja",
                        ).getOrFail(),
                        Vurderingsperiode.Uføre.tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            vurderingsperiode = Periode.create(1.september(2021), 31.desember(2021)),
                            begrunnelse = "nei",
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
                        )
                        it.dato shouldBe 1.januar(2021)
                    }
                }
        }

        @Test
        fun `alle vilkår uavklart gir uavklart`() {
            Vilkårsvurderinger.Revurdering.IkkeVurdert
                .let {
                    it.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(it.vilkår)
                }
        }

        @Test
        fun `et vilkår uavklart gir uavklart`() {
            vilkårsvurderingerInnvilgetRevurdering(
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
            Vilkårsvurderinger.Revurdering.IkkeVurdert.resultat shouldBe Vilkårsvurderingsresultat.Uavklart(
                setOf(
                    Vilkår.Uførhet.IkkeVurdert,
                    Vilkår.Formue.IkkeVurdert,
                    OppholdIUtlandetVilkår.IkkeVurdert,
                ),
            )
        }

        @Test
        fun `oppdaterer perioden på alle vilkår`() {
            val gammel = Periode.create(1.januar(2021), 31.desember(2021))
            val ny = Periode.create(1.juli(2021), 31.desember(2021))

            vilkårsvurderingerInnvilgetRevurdering(periode = gammel)
                .let {
                    it.periode shouldBe gammel
                    it.oppdaterStønadsperiode(Stønadsperiode.create(ny, "")).periode shouldBe ny
                }
        }

        @Test
        fun `periode for ikke vurderte vilkår`() {
            Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert.periode shouldBe null
            Vilkårsvurderinger.Revurdering.IkkeVurdert.periode shouldBe null
        }

        @Test
        fun `periode for vurderte vilkår`() {
            Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert.periode shouldBe null
            Vilkårsvurderinger.Revurdering.IkkeVurdert.periode shouldBe null
        }

        @Test
        fun `likhet`() {
            val a = vilkårsvurderingerInnvilgetRevurdering()
            val b = vilkårsvurderingerInnvilgetRevurdering()
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
            val a = vilkårsvurderingerInnvilgetRevurdering(periode = periodeMai2021)
            val b = vilkårsvurderingerInnvilgetRevurdering(periode = periodeJuli2021)

            a shouldBe b
            (a == b) shouldBe true
            a.erLik(b) shouldBe true
        }
    }
}
