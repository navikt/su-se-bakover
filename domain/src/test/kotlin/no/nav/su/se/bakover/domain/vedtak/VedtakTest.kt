package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
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
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTest
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
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
                        vurderingsperiode = vurderingsperiode,
                        behandlingsperiode = vurderingsperiode,
                        bosituasjon = bosituasjon,
                    ),
                ),
            ),
        )
    }

    private fun lagFormueVurderingsperiode(
        vurderingsperiodeId: UUID = UUID.randomUUID(),
        formuegrunnlagId: UUID = UUID.randomUUID(),
        vurderingsperiode: Periode,
        behandlingsperiode: Periode,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
    ): Vurderingsperiode.Formue {
        return Vurderingsperiode.Formue.tryCreateFromGrunnlag(
            id = vurderingsperiodeId,
            grunnlag = Formuegrunnlag.create(
                id = formuegrunnlagId,
                opprettet = fixedTidspunkt,
                periode = vurderingsperiode,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                bosituasjon = bosituasjon,
                behandlingsPeriode = behandlingsperiode,
            ),
            formuegrenserFactory = formuegrenserFactoryTest,
        ).also {
            assert(it.resultat == Resultat.Innvilget)
        }
    }

    @Test
    fun `lager tidslinje for enkelt vedtak`() {
        val periode = år(2021)
        val bosituasjon = lagFullstendigBostiuasjon(periode)

        val vedtak = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(bosituasjon),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                lagVurdertUføreVilkår(periode),
                lagVurdertFormueVilkår(periode, bosituasjon),
            ),
        )
        listOf(vedtak).lagTidslinje(
            år(2021),
        ).tidslinje.let { tidslinje ->
            tidslinje.size shouldBe 1
            tidslinje[0].shouldBeEqualToExceptId(
                VedtakSomKanRevurderes.VedtakPåTidslinje(
                    opprettet = vedtak.opprettet,
                    periode = vedtak.periode,
                    grunnlagsdata = vedtak.behandling.grunnlagsdata,
                    vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger,
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
        val periodeA = år(2021)
        val bosituasjonA = lagFullstendigBostiuasjon(periodeA)
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjonA)),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                lagVurdertUføreVilkår(
                    vurderingsperiode = periodeA,
                ),
                lagVurdertFormueVilkår(
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
            grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjonB)),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                lagVurdertUføreVilkår(periodeB),
                lagVurdertFormueVilkår(periodeB, bosituasjonB),
            ),
        )
        listOf(a, b).lagTidslinje(
            år(2021),
        ).tidslinje.let {
            it.size shouldBe 2
            it.first().shouldBeEqualToExceptId(
                expected = VedtakSomKanRevurderes.VedtakPåTidslinje(
                    opprettet = Tidspunkt.now(fixedClockWithRekkefølge(1)),
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    grunnlagsdata = Grunnlagsdata.create(
                        bosituasjon = listOf(
                            lagFullstendigBostiuasjon(Periode.create(1.januar(2021), 30.april(2021))),
                        ),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        lagVurdertUføreVilkår(
                            vurderingsperiode = Periode.create(1.januar(2021), 30.april(2021)),
                        ),
                        lagVurdertFormueVilkår(
                            Periode.create(1.januar(2021), 30.april(2021)),
                            lagFullstendigBostiuasjon(Periode.create(1.januar(2021), 30.april(2021))),
                        ),
                    ),
                    originaltVedtak = a,
                ),
            )

            it.last().shouldBeEqualToExceptId(
                expected = VedtakSomKanRevurderes.VedtakPåTidslinje(
                    opprettet = Tidspunkt.now(fixedClockWithRekkefølge(2)),
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    grunnlagsdata = b.behandling.grunnlagsdata,
                    vilkårsvurderinger = b.behandling.vilkårsvurderinger,
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
        val p1 = år(2021)
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(lagFullstendigBostiuasjon(p1)),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                lagVurdertUføreVilkår(p1),
                lagVurdertFormueVilkår(p1, lagFullstendigBostiuasjon(p1)),
            ),
        )

        val p2 = Periode.create(1.mai(2021), 31.desember(2021))
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = p2.fraOgMed,
            tilDato = p2.tilOgMed,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(lagFullstendigBostiuasjon(p2)),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                lagVurdertUføreVilkår(p2),
                lagVurdertFormueVilkår(p2, lagFullstendigBostiuasjon(p2)),

            ),
        )
        listOf(a, b).lagTidslinje(
            år(2021),
        ).tidslinje.let { tidslinje ->
            tidslinje.size shouldBe 2

            val firstPeriode = Periode.create(1.januar(2021), 30.april(2021))
            val firstBosituasjon = lagFullstendigBostiuasjon(firstPeriode)
            tidslinje.first().shouldBeEqualToExceptId(
                VedtakSomKanRevurderes.VedtakPåTidslinje(
                    periode = firstPeriode,
                    opprettet = a.opprettet,
                    grunnlagsdata = Grunnlagsdata.create(
                        fradragsgrunnlag = listOf(),
                        bosituasjon = listOf(firstBosituasjon),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        lagVurdertUføreVilkår(firstPeriode),
                        lagVurdertFormueVilkår(firstPeriode, firstBosituasjon),
                    ),
                    originaltVedtak = a,
                ),
            )

            val lastPeriode = Periode.create(1.mai(2021), 31.desember(2021))
            val lastBostiuasjon = lagFullstendigBostiuasjon(lastPeriode)
            tidslinje.last().shouldBeEqualToExceptId(
                VedtakSomKanRevurderes.VedtakPåTidslinje(
                    periode = lastPeriode,
                    opprettet = b.opprettet,
                    grunnlagsdata = Grunnlagsdata.create(
                        fradragsgrunnlag = listOf(),
                        bosituasjon = listOf(lastBostiuasjon),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                        lagVurdertUføreVilkår(lastPeriode),
                        lagVurdertFormueVilkår(lastPeriode, lastBostiuasjon),
                    ),
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
        val p1 = år(2021)
        val b1 = lagFullstendigBostiuasjon(p1)
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(b1),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                lagVurdertUføreVilkår(p1),
                lagVurdertFormueVilkår(p1, b1),
            ),
        )

        val p2 = år(2021)
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
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(b2),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(uføreVurderingB)),
                lagVurdertFormueVilkår(p2, b2),
            ),
        )

        val actual =
            listOf(a, b).lagTidslinje(
                periode = år(2021),
            ).tidslinje.let {
                it.size shouldBe 1
                it[0]
            }

        actual.shouldBeEqualToExceptId(
            VedtakSomKanRevurderes.VedtakPåTidslinje(
                opprettet = b.opprettet,
                periode = b.periode,
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = listOf(),
                    bosituasjon = listOf(b2),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(

                    Vilkår.Uførhet.Vurdert.create(
                        nonEmptyListOf(uføreVurderingB),
                    ),
                    lagVurdertFormueVilkår(p2, b2),
                ),
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
        vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling,
    ): VedtakSomKanRevurderes.EndringIYtelse {
        val clock = fixedClockWithRekkefølge(rekkefølge)
        return VedtakSomKanRevurderes.fromSøknadsbehandling(
            søknadsbehandling = Søknadsbehandling.Iverksatt.Innvilget(
                id = mock(),
                opprettet = Tidspunkt.now(clock),
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(2222),
                søknad = mock(),
                oppgaveId = mock(),
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                fnr = Fnr.generer(),
                beregning = mock(),
                simulering = mock(),
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("Attes T. Ant"),
                            opprettet = Tidspunkt.now(clock),
                        ),
                    ),
                ),
                fritekstTilBrev = "",
                stønadsperiode = Stønadsperiode.create(periode = Periode.create(fraDato, tilDato)),
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående,
            ),
            utbetalingId = UUID30.randomUUID(),
            clock = clock,
        )
    }
}
