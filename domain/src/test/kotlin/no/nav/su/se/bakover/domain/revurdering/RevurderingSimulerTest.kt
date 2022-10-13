package no.nav.su.se.bakover.domain.revurdering

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForOpphør
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simuleringOpphørt
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

class RevurderingSimulerTest {
    @Test
    fun `avkortingsvarsel dersom opphør skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        simulertRevurdering(
            vilkårOverrides = listOf(utenlandsoppholdAvslag()),
        ).let { (_, revurdering) ->
            revurdering.avkorting.let {
                (it as AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel).let { avkorting ->
                    avkorting.avkortingsvarsel shouldBe Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        id = avkorting.avkortingsvarsel.id,
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        opprettet = avkorting.avkortingsvarsel.opprettet,
                        simulering = avkorting.avkortingsvarsel.simulering,
                    ).skalAvkortes()
                }
            }
        }
    }

    @Test
    fun `kaster exception dersom simulering med justert opphørsdato for utbetaling inneholder feilutbetalinger`() {
        assertThrows<IllegalStateException> {
            beregnetRevurdering(
                vilkårOverrides = listOf(utenlandsoppholdAvslag()),
            ).let { (sak, revurdering) ->
                revurdering.shouldBeType<BeregnetRevurdering.Opphørt>().let { beregnet ->
                    beregnet.simuler(
                        saksbehandler = saksbehandler,
                        clock = fixedClock,
                        lagUtbetaling = sak::lagUtbetalingForOpphør,
                        eksisterendeUtbetalinger = sak::utbetalinger,
                    ) { utbetaling, eksisterende, opphørsperiode ->
                        beregnet.periode shouldNotBe opphørsperiode
                        utbetaling.toSimulertUtbetaling(
                            simuleringOpphørt(
                                opphørsperiode = beregnet.periode, // bruk feil periode
                                eksisterendeUtbetalinger = eksisterende,
                                fnr = beregnet.fnr,
                                sakId = beregnet.sakId,
                                saksnummer = beregnet.saksnummer,
                                clock = fixedClock,
                            ),
                        ).right()
                    }.getOrFail()
                }
            }
        }
    }

    @Test
    fun `ingen avkortingsvarsel dersom opphør ikke skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        simulertRevurdering(
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (_, revurdering) ->
            revurdering.simulering.harFeilutbetalinger() shouldBe true
            revurdering.avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
        }
    }

    @Test
    fun `ingen avkortingsvarsel dersom opphør ikke fører til feilutbetaling`() {
        val revurderingsperiode = Periode.create(1.september(2021), 31.desember(2021))
        simulertRevurdering(
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = listOf(utenlandsoppholdAvslag(periode = revurderingsperiode)),
        ).let { (_, revurdering) ->
            revurdering.simulering.harFeilutbetalinger() shouldBe false
            revurdering.avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
        }
    }

    @Test
    fun `oppretter tilbakekrevingsbehandling dersom simulering inneholder feilutbetaling som ikke skyldes utlandsopphold`() {
        beregnetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mars(2021),
                    arbeidsinntekt = 15799.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, beregnet) ->
            (beregnet as BeregnetRevurdering.Innvilget).let {
                when (
                    it.simuler(
                        saksbehandler = saksbehandler,
                        clock = fixedClock,
                    ) {
                        nyUtbetalingSimulert(
                            sakOgBehandling = sak to beregnet,
                            beregning = it.beregning,
                            clock = fixedClock,
                        ).right()
                    }.getOrFail().tilbakekrevingsbehandling
                ) {
                    is IkkeAvgjort -> {}
                    else -> fail("Skulle opprettet tilbakekrevingsbehandling")
                }
            }
        }
    }
}
