package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulerOpphør
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RevurderingSimulerTest {
    @Test
    fun `avkortingsvarsel dersom opphør skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        simulertRevurdering(
            vilkårOverrides = listOf(utenlandsoppholdAvslag()),
        ).let { (_, revurdering) ->
            revurdering.simulering.harFeilutbetalinger() shouldBe false
            revurdering.avkorting.let {
                it.shouldBeType<AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel>().let { avkorting ->
                    avkorting.avkortingsvarsel shouldBe Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        id = avkorting.avkortingsvarsel.id,
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        opprettet = avkorting.avkortingsvarsel.opprettet,
                        simulering = avkorting.avkortingsvarsel.simulering,
                    ).skalAvkortes()
                    avkorting.avkortingsvarsel.simulering.harFeilutbetalinger() shouldBe true
                }
            }
        }
    }

    @Test
    fun `kaster exception dersom simulering med justert opphørsdato for utbetaling inneholder feilutbetalinger`() {
        assertThrows<IllegalStateException> {
            beregnetRevurdering(
                vilkårOverrides = listOf(utenlandsoppholdAvslag()),
                clock = tikkendeFixedClock,
            ).let { (sak, revurdering) ->
                revurdering.shouldBeType<BeregnetRevurdering.Opphørt>().let { beregnet ->
                    beregnet.simuler(
                        saksbehandler = saksbehandler,
                        clock = tikkendeFixedClock,
                        simuler = { _, _ ->
                            simulerOpphør(
                                sak = sak,
                                revurdering = beregnet,
                                simuleringsperiode = beregnet.periode, // bruk feil periode
                            )
                        },
                    ).tap {
                        it.simulering.harFeilutbetalinger() shouldBe true
                    }
                }.getOrFail()
            }
        }.also {
            it.message shouldBe "Simulering med justert opphørsdato for utbetalinger pga avkorting utenlandsopphold inneholder feilutbetaling, se sikkerlogg for detaljer"
        }
    }

    @Test
    fun `ingen avkortingsvarsel dersom opphør ikke skyldes utenlandsopphold og simulering inneholder feilutbetaling`() {
        simulertRevurdering(
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (_, revurdering) ->
            revurdering.simulering.harFeilutbetalinger() shouldBe true
            revurdering.shouldBeType<SimulertRevurdering.Opphørt>().also {
                it.avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
            }
        }
    }

    @Test
    fun `ingen avkortingsvarsel dersom opphør ikke fører til feilutbetaling`() {
        val revurderingsperiode = Periode.create(1.september(2021), 31.desember(2021))
        simulertRevurdering(
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = listOf(utenlandsoppholdAvslag(periode = revurderingsperiode)),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = revurderingsperiode,
                    arbeidsinntekt = 35000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (_, revurdering) ->
            revurdering.simulering.harFeilutbetalinger() shouldBe false
            revurdering.shouldBeType<SimulertRevurdering.Opphørt>().also {
                it.avkorting shouldBe AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
            }
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
            beregnet.shouldBeType<BeregnetRevurdering.Innvilget>().also {
                it.simuler(
                    saksbehandler = saksbehandler,
                    clock = fixedClock,
                    simuler = { _, _ ->
                        simulerUtbetaling(
                            sak = sak,
                            revurdering = beregnet,
                        ).map {
                            it.simulering
                        }
                    },
                ).getOrFail().tilbakekrevingsbehandling.shouldBeType<IkkeAvgjort>()
            }
        }
    }
}
