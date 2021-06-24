package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.fixedClock
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VedtakTest {

    private fun lagFullstendigBostiuasjon(periode: Periode): Grunnlag.Bosituasjon.Fullstendig {
        return Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            begrunnelse = null,
        )
    }

    private fun lagVurdertUføreVilkår(vurderingsperiode: Periode): Vilkår.Uførhet.Vurdert {
        return Vilkår.Uførhet.Vurdert.create(
            NonEmptyList.fromListUnsafe(
                listOf(lagUføreVurderingsperiode(UUID.randomUUID(), vurderingsperiode, vurderingsperiode)),
            ),
        )
    }

    private fun lagUføreVurderingsperiode(
        id: UUID,
        vurderingsperiode: Periode,
        uføregrunnlagPeriode: Periode,
    ): Vurderingsperiode.Uføre {
        return Vurderingsperiode.Uføre.create(
            id = id,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = id,
                opprettet = fixedTidspunkt,
                periode = uføregrunnlagPeriode,
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            periode = vurderingsperiode,
            begrunnelse = null,
        )
    }

    private fun lagVurdertFormueVilkår(
        vurderingsperiode: Periode,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
    ): Vilkår.Formue.Vurdert {
        return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
            NonEmptyList.fromListUnsafe(
                listOf(
                    lagFormueVurderingsperiode(
                        id = UUID.randomUUID(),
                        vurderingsperiode = vurderingsperiode,
                        behandlingsperiode = vurderingsperiode,
                        bosituasjon = bosituasjon,
                    ),
                ),
            ),
        )
    }

    private fun lagFormueVurderingsperiode(
        id: UUID,
        vurderingsperiode: Periode,
        behandlingsperiode: Periode,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
    ): Vurderingsperiode.Formue {
        return Vurderingsperiode.Formue.create(
            id = id,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Formuegrunnlag.create(
                id = id,
                opprettet = fixedTidspunkt,
                periode = vurderingsperiode,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                bosituasjon = bosituasjon,
                behandlingsPeriode = behandlingsperiode,
            ),
            periode = vurderingsperiode,
        )
    }

    @Test
    fun `lager tidslinje for enkelt vedtak`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val bosituasjon = lagFullstendigBostiuasjon(periode)

        val vedtak = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(bosituasjon),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = lagVurdertUføreVilkår(periode),
                formue = lagVurdertFormueVilkår(periode, bosituasjon),
            ),
        )
        listOf(vedtak).lagTidslinje(
            Periode.create(1.januar(2021), 31.desember(2021)),
            fixedClock,
        ).tidslinje.let { tidslinje ->
            tidslinje.size shouldBe 1
            tidslinje[0].shouldBeEqualToExceptId(
                Vedtak.VedtakPåTidslinje(
                    opprettet = vedtak.opprettet,
                    periode = vedtak.periode,
                    grunnlagsdata = vedtak.behandling.grunnlagsdata,
                    vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger,
                    fradrag = vedtak.beregning.getFradrag(),
                    originaltVedtak = vedtak,
                ),
            )
        }
    }

    /**
     *  |––––-----| a
     *      |–––––| b
     *  |---|-----| resultat
     */
    @Test
    fun `lager tidslinje for flere vedtak`() {
        val periodeA = Periode.create(1.januar(2021), 31.desember(2021))
        val bosituasjonA = lagFullstendigBostiuasjon(periodeA)
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjonA)),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = lagVurdertUføreVilkår(
                    vurderingsperiode = periodeA,
                ),
                formue = lagVurdertFormueVilkår(
                    periodeA,
                    bosituasjonA,
                ),
            ),
        )

        val periodeB = Periode.create(1.mai(2021), 31.desember(2021))
        val bosituasjonB = lagFullstendigBostiuasjon(periodeB)
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.mai(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjonB)),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = lagVurdertUføreVilkår(periodeB),
                formue = lagVurdertFormueVilkår(periodeB, bosituasjonB),
            ),
        )
        listOf(a, b).lagTidslinje(
            Periode.create(
                1.januar(2021),
                31.desember(2021),
            ),
            clock = no.nav.su.se.bakover.domain.fixedClock,
        ).tidslinje.let {
            it.size shouldBe 2
            it.first().shouldBeEqualToExceptId(
                expected = Vedtak.VedtakPåTidslinje(
                    opprettet = Tidspunkt.now(fixedClockWithRekkefølge(1)),
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    grunnlagsdata = a.behandling.grunnlagsdata.copy(
                        bosituasjon = listOf(
                            lagFullstendigBostiuasjon(Periode.create(1.januar(2021), 30.april(2021))),
                        ),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger(
                        uføre = lagVurdertUføreVilkår(
                            vurderingsperiode = Periode.create(1.januar(2021), 30.april(2021)),
                        ),
                        formue = lagVurdertFormueVilkår(
                            Periode.create(1.januar(2021), 30.april(2021)),
                            lagFullstendigBostiuasjon(Periode.create(1.januar(2021), 30.april(2021))),
                        ),
                    ),
                    fradrag = a.behandling.grunnlagsdata.fradragsgrunnlag.map { it.fradrag },
                    originaltVedtak = a,
                ),
            )

            it.last().shouldBeEqualToExceptId(
                expected = Vedtak.VedtakPåTidslinje(
                    opprettet = Tidspunkt.now(fixedClockWithRekkefølge(2)),
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    grunnlagsdata = b.behandling.grunnlagsdata,
                    vilkårsvurderinger = b.behandling.vilkårsvurderinger,
                    fradrag = b.behandling.grunnlagsdata.fradragsgrunnlag.map { it.fradrag },
                    originaltVedtak = b,
                ),
            )
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
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(lagFullstendigBostiuasjon(p1)),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = lagVurdertUføreVilkår(p1),
                formue = lagVurdertFormueVilkår(p1, lagFullstendigBostiuasjon(p1)),
            ),
        )

        val p2 = Periode.create(1.mai(2021), 31.desember(2021))
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = p2.fraOgMed,
            tilDato = p2.tilOgMed,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(lagFullstendigBostiuasjon(p2)),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = lagVurdertUføreVilkår(p2),
                formue = lagVurdertFormueVilkår(p2, lagFullstendigBostiuasjon(p2)),
            ),
        )
        listOf(a, b).lagTidslinje(
            Periode.create(1.januar(2021), 31.desember(2021)),
            fixedClock,
        ).tidslinje.let { tidslinje ->
            tidslinje.size shouldBe 2

            val firstPeriode = Periode.create(1.januar(2021), 30.april(2021))
            val firstBosituasjon = lagFullstendigBostiuasjon(firstPeriode)
            tidslinje.first().shouldBeEqualToExceptId(
                Vedtak.VedtakPåTidslinje(
                    periode = firstPeriode,
                    opprettet = a.opprettet,
                    grunnlagsdata = Grunnlagsdata(
                        fradragsgrunnlag = listOf(),
                        bosituasjon = listOf(firstBosituasjon),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger(
                        uføre = lagVurdertUføreVilkår(firstPeriode),
                        formue = lagVurdertFormueVilkår(firstPeriode, firstBosituasjon),
                    ),
                    fradrag = emptyList(),
                    originaltVedtak = a,
                ),
            )

            val lastPeriode = Periode.create(1.mai(2021), 31.desember(2021))
            val lastBostiuasjon = lagFullstendigBostiuasjon(lastPeriode)
            tidslinje.last().shouldBeEqualToExceptId(
                Vedtak.VedtakPåTidslinje(
                    periode = lastPeriode,
                    opprettet = b.opprettet,
                    grunnlagsdata = Grunnlagsdata(
                        fradragsgrunnlag = listOf(),
                        bosituasjon = listOf(lastBostiuasjon),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger(
                        uføre = lagVurdertUføreVilkår(lastPeriode),
                        formue = lagVurdertFormueVilkår(lastPeriode, lastBostiuasjon),
                    ),
                    fradrag = emptyList(),
                    originaltVedtak = b,
                ),
            )
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
        val b1 = lagFullstendigBostiuasjon(p1)
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(b1),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = lagVurdertUføreVilkår(p1),
                formue = lagVurdertFormueVilkår(p1, b1),
            ),
        )

        val p2 = Periode.create(1.januar(2021), 31.desember(2021))
        val b2 = lagFullstendigBostiuasjon(p2)
        val uføreVurderingB = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Avslag,
            grunnlag = null,
            periode = p2,
            begrunnelse = "denne personen får et avslag fordi john ikke liker han",
        )

        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(b2),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(uføreVurderingB)),
                formue = lagVurdertFormueVilkår(p2, b2),
            ),
        )

        val actual =
            listOf(a, b).lagTidslinje(
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                fixedClock,
            ).tidslinje.let {
                it.size shouldBe 1
                it[0]
            }

        actual.shouldBeEqualToExceptId(
            Vedtak.VedtakPåTidslinje(
                opprettet = b.opprettet,
                periode = b.periode,
                grunnlagsdata = Grunnlagsdata(
                    fradragsgrunnlag = listOf(),
                    bosituasjon = listOf(b2),
                ),
                vilkårsvurderinger = Vilkårsvurderinger(
                    uføre = Vilkår.Uførhet.Vurdert.create(
                        nonEmptyListOf(uføreVurderingB),
                    ),
                    formue = lagVurdertFormueVilkår(p2, b2),
                ),
                fradrag = b.beregning.getFradrag(),
                originaltVedtak = b,
            ),
        )
    }

    private fun fixedClockWithRekkefølge(rekkefølge: Long, clock: Clock = fixedClock): Clock {
        return Clock.fixed(clock.instant().plus(rekkefølge, ChronoUnit.DAYS), ZoneOffset.UTC)
    }

    private fun lagVedtak(
        rekkefølge: Long,
        fraDato: LocalDate,
        tilDato: LocalDate,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger,
    ): Vedtak.EndringIYtelse {
        val clock = fixedClockWithRekkefølge(rekkefølge)
        return Vedtak.fromSøknadsbehandling(
            søknadsbehandling = Søknadsbehandling.Iverksatt.Innvilget(
                id = mock(),
                opprettet = Tidspunkt.now(clock),
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
            utbetalingId = UUID30.randomUUID(),
            clock = clock,
        )
    }
}
