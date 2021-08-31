package no.nav.su.se.bakover.domain.revurdering

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.innvilgetFormueVilkår
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.lagGrunnlagsdata
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID

internal class RevurderingTest {

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Avslag,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
        ).beregn(eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(20000, periode)))).orNull()!!
            .let {
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.UFØRHET)
            }
    }

    @Test
    fun `beregning gir ikke opphør hvis vilkår er oppfylt`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
        ).beregn(eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(20000, periode)))).orNull()!!
            .let {
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            }
    }

    @Test
    fun `beregningen gir ikke opphør dersom beløpet er under minstegrense, men endringen er mindre enn 10 prosent`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
            fradrag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 20535.0,
                    periode = Periode.create(periode.fraOgMed, 30.april(2021)),
                    tilhører = FradragTilhører.BRUKER,
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 21735.0,
                    periode = Periode.create(1.mai(2021), periode.tilOgMed),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).beregn(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(lagUtbetalingslinje(440, periode)),
            ),
        ).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            it.beregning.alleMånederErUnderMinstebeløp() shouldBe true
        }
    }

    @Test
    fun `beregning med beløpsendring større enn 10 prosent fører til endring`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
        ).beregn(eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(14000, periode)))).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent fører ikke til endring`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
        ).beregn(eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(20000, periode)))).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent fører til endring - g regulering`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).beregn(eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(20000, periode)))).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
        }
    }

    @Test
    fun `beregning uten beløpsendring fører til ingen endring - g regulering`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).beregn(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(lagUtbetalingslinje(20946, Periode.create(periode.fraOgMed, 30.april(2021)))),
                lagUtbetaling(lagUtbetalingslinje(21989, Periode.create(1.mai(2021), periode.tilOgMed))),
            ),
        ).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør - g regulering`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = null,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            fradrag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 350_000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),

            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).beregn(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(14000, periode))),
        ).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
            (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periode,
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = 0,
            opprettet = fixedTidspunkt,
        )
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
            fradrag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 350_000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).beregn(eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(14000, periode)))).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
            (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
        }
    }

    @Test
    fun `beregning som fører til beløp under minstegrense gir opphør`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periode,
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = 0,
            opprettet = fixedTidspunkt,
        )
        lagRevurdering(
            periode = periode,
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(),
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = periode,
                            begrunnelse = null,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
            fradrag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 20800.0,
                    periode = Periode.create(periode.fraOgMed, 30.april(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 21800.0,
                    periode = Periode.create(1.mai(2021), periode.tilOgMed),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).beregn(eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(14000, periode)))).orNull()!!.let {
            it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
            (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
        }
    }

    private fun lagRevurdering(
        tilRevurdering: Vedtak.EndringIYtelse = mock(),
        vilkårsvurderinger: Vilkårsvurderinger,
        fradrag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
        bosituasjon: List<Grunnlag.Bosituasjon>,
        periode: Periode,
        revurderingsårsak: Revurderingsårsak = Revurderingsårsak(
            årsak = Revurderingsårsak.Årsak.INFORMASJON_FRA_KONTROLLSAMTALE,
            begrunnelse = Revurderingsårsak.Begrunnelse.create(value = "b"),
        ),
    ) = OpprettetRevurdering(
        id = UUID.randomUUID(),
        periode = periode,
        opprettet = Tidspunkt.now(),
        tilRevurdering = tilRevurdering,
        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
        oppgaveId = OppgaveId(value = "oppgaveId"),
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        vilkårsvurderinger = vilkårsvurderinger,
        grunnlagsdata = lagGrunnlagsdata(
            bosituasjon = bosituasjon,
            fradragsgrunnlag = fradrag,
        ),
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        attesteringer = Attesteringshistorikk.empty(),
    )

    private fun lagUtbetaling(
        vararg utbetalingslinjer: Utbetalingslinje,
    ) = Utbetaling.OversendtUtbetaling.MedKvittering(
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(9999),
        fnr = Fnr.generer(),
        utbetalingslinjer = NonEmptyList.fromListUnsafe(utbetalingslinjer.toList()),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = mock(),
        avstemmingsnøkkel = mock(),
        simulering = mock(),
        utbetalingsrequest = mock(),
        kvittering = mock(),
    )

    private fun lagUtbetalingslinje(månedsbeløp: Int, periode: Periode) = Utbetalingslinje.Ny(
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
    )
}
