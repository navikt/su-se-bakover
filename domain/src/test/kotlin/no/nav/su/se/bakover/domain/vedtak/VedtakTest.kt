package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.formueVilkår
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VedtakTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `lager tidslinje for enkelt vedtak`() {
        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = null,
        )
        val vedtak = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    bosituasjon,
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )
        listOf(vedtak).lagTidslinje(Periode.create(1.januar(2021), 31.desember(2021))).tidslinje.let { tidslinje ->
            tidslinje.size shouldBe 1
            tidslinje[0].shouldBeEqualToIgnoringFields(
                Vedtak.VedtakPåTidslinje(
                    opprettet = vedtak.opprettet,
                    periode = vedtak.periode,
                    grunnlagsdata = vedtak.behandling.grunnlagsdata, // ignorert
                    vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger, // ignorert
                    fradrag = vedtak.beregning.getFradrag(),
                    originaltVedtak = vedtak,
                ),
                Vedtak.VedtakPåTidslinje::grunnlagsdata, Vedtak.VedtakPåTidslinje::vilkårsvurderinger,
            )
            tidslinje[0].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.vilkårsvurderinger.uføre shouldBe Vilkår.Uførhet.IkkeVurdert
                vedtakPåTidslinje.grunnlagsdata.bosituasjon shouldBe listOf(
                    bosituasjon.copy(
                        id = vedtakPåTidslinje.grunnlagsdata.bosituasjon[0].id,
                    ),
                )
                vedtakPåTidslinje.grunnlagsdata.fradragsgrunnlag shouldBe emptyList()
                // TODO jah: Legg på assert på formue
            }
        }
    }

    /**
     *  |––––|      a
     *      |–––––| b
     *  |---|-----| resultat
     */
    @Test
    fun `lager tidslinje for flere vedtak`() {
        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = null,
        )
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjon)),
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.mai(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjon)),
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )
        listOf(a, b).lagTidslinje(
            Periode.create(
                1.januar(2021),
                31.desember(2021),
            ),
        ).tidslinje.let {
            shouldBeEqualToIgnoringFields(
                Vedtak.VedtakPåTidslinje(
                    opprettet = a.opprettet,
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    grunnlagsdata = a.behandling.grunnlagsdata,
                    vilkårsvurderinger = a.behandling.vilkårsvurderinger,
                    fradrag = a.beregning.getFradrag(),
                    originaltVedtak = a,
                ),
                Vedtak.VedtakPåTidslinje::grunnlagsdata,
                Vedtak.VedtakPåTidslinje::vilkårsvurderinger,
            )
            shouldBeEqualToIgnoringFields(
                Vedtak.VedtakPåTidslinje(
                    opprettet = b.opprettet,
                    periode = b.periode,
                    grunnlagsdata = b.behandling.grunnlagsdata,
                    vilkårsvurderinger = b.behandling.vilkårsvurderinger,
                    fradrag = b.beregning.getFradrag(),
                    originaltVedtak = b,
                ),
                Vedtak.VedtakPåTidslinje::grunnlagsdata,
                Vedtak.VedtakPåTidslinje::vilkårsvurderinger,
            )
            // TODO jah: Legg på tester på grunnlagsdata og vilkårsvurderinger
        }
    }

    /**
     *  |-––––-|      a
     *  |------|      u1
     *       |------| b
     *       |------| u2
     *  |----|------| resultat
     *  |-u1-|--u2--|
     */
    @Test
    fun `begrenser perioden på grunnlagene til samme perioden som vedtaket`() {
        val p1 = Periode.create(1.januar(2021), 31.desember(2021))
        val u1 = lagUføregrunnlag(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
        )
        val v1 = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = u1,
            periode = p1,
            begrunnelse = "hei",
        )
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = p1,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(v1)),
                formue = formueVilkår(p1),
            ),
        )

        val p2 = Periode.create(1.mai(2021), 31.desember(2021))
        val u2 = lagUføregrunnlag(
            rekkefølge = 2,
            fraDato = p2.fraOgMed,
            tilDato = p2.tilOgMed,
        )
        val v2 = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Avslag,
            grunnlag = u2,
            periode = p2,
            begrunnelse = "hei",
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = p2.fraOgMed,
            tilDato = p2.tilOgMed,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = p2,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(v2)),
                formue = formueVilkår(p1),
            ),
        )
        listOf(a, b).lagTidslinje(Periode.create(1.januar(2021), 31.desember(2021))).tidslinje.let { tidslinje ->
            tidslinje[0].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.originaltVedtak shouldBe a
                vedtakPåTidslinje.opprettet shouldBe a.opprettet
                vedtakPåTidslinje.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                vedtakPåTidslinje.vilkårsvurderinger.uføre.grunnlag[0].let {
                    it.id shouldNotBe u1.id
                    it.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                    it.uføregrad shouldBe u1.uføregrad
                    it.forventetInntekt shouldBe u1.forventetInntekt
                }
                (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).let { vilkårcopy ->
                    vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                        vurderingsperiodecopy.id shouldNotBe v1.id
                        vurderingsperiodecopy.begrunnelse shouldBe v1.begrunnelse
                        vurderingsperiodecopy.resultat shouldBe v1.resultat
                        vurderingsperiodecopy.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                        vurderingsperiodecopy.grunnlag!!.let {
                            it.id shouldNotBe u1.id
                            it.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                            it.uføregrad shouldBe u1.uføregrad
                            it.forventetInntekt shouldBe u1.forventetInntekt
                        }
                    }
                }
            }
            tidslinje[1].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.originaltVedtak shouldBe b
                vedtakPåTidslinje.opprettet shouldBe b.opprettet
                vedtakPåTidslinje.periode shouldBe b.periode
                vedtakPåTidslinje.vilkårsvurderinger.uføre.grunnlag[0].let {
                    it.id shouldNotBe u2.id
                    it.periode shouldBe u2.periode
                    it.uføregrad shouldBe u2.uføregrad
                    it.forventetInntekt shouldBe u2.forventetInntekt
                }
                (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).let { vilkårcopy ->
                    vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                        vurderingsperiodecopy.id shouldNotBe v2.id
                        vurderingsperiodecopy.begrunnelse shouldBe v2.begrunnelse
                        vurderingsperiodecopy.resultat shouldBe v2.resultat
                        vurderingsperiodecopy.periode shouldBe v2.periode
                        vurderingsperiodecopy.grunnlag!!.let {
                            it.id shouldNotBe u2.id
                            it.periode shouldBe u2.periode
                            it.uføregrad shouldBe u2.uføregrad
                            it.forventetInntekt shouldBe u2.forventetInntekt
                        }
                    }
                }
            }
        }
    }

    /**
     *  |------| a
     *  |------| u1
     *  |––––––| b
     *           u2 (fjernet)
     *  |------| resultat
     *           (ingen uføregrunnlag)
     */
    @Test
    fun `informasjon som overskrives av nyere vedtak forsvinner fra tidslinjen`() {
        val p1 = Periode.create(1.januar(2021), 31.desember(2021))
        val u1 = lagUføregrunnlag(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
        )
        fun uføreVurderingsperiode() = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = u1,
            periode = p1,
            begrunnelse = "hei",
        )
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = p1,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(uføreVurderingsperiode())),
                formue = formueVilkår(p1),
            ),
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(uføreVurderingsperiode())),
                formue = formueVilkår(p1),
            ),
        )

        val actual =
            listOf(a, b).lagTidslinje(periode = Periode.create(1.januar(2021), 31.desember(2021))).tidslinje.let {
                it.size shouldBe 1
                it[0]
            }
        actual.shouldBeEqualToIgnoringFields(
            Vedtak.VedtakPåTidslinje(
                opprettet = b.opprettet,
                periode = b.periode,
                grunnlagsdata = Grunnlagsdata.EMPTY, // ignorert
                vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert, // ignorert
                fradrag = b.beregning.getFradrag(),
                originaltVedtak = b,
            ),
            Vedtak.VedtakPåTidslinje::grunnlagsdata, Vedtak.VedtakPåTidslinje::vilkårsvurderinger,
        )
        // TODO jah: Legg til testing av innhold av grunnlagsdata og vilkårsvurderinger med Jacob
    }

    private fun lagUføregrunnlag(rekkefølge: Long, fraDato: LocalDate, tilDato: LocalDate) = Grunnlag.Uføregrunnlag(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
        periode = Periode.create(fraDato, tilDato),
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = 0,
    )

    private fun lagVedtak(
        rekkefølge: Long,
        fraDato: LocalDate,
        tilDato: LocalDate,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger,
    ) = Vedtak.fromSøknadsbehandling(
        Søknadsbehandling.Iverksatt.Innvilget(
            id = mock(),
            opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2222),
            søknad = mock(),
            oppgaveId = mock(),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            fnr = FnrGenerator.random(),
            beregning = mock(),
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
            fritekstTilBrev = "",
            stønadsperiode = Stønadsperiode.create(
                periode = Periode.create(fraDato, tilDato),
                begrunnelse = "begrunnelsen for perioden",
            ),
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ),
        UUID30.randomUUID(),
    )
}
