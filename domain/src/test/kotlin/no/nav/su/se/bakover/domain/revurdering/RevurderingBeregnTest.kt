package no.nav.su.se.bakover.domain.revurdering

import arrow.core.NonEmptyList
import arrow.core.left
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avslåttFormueVilkår
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.math.abs

internal class RevurderingBeregnTest {

    @Test
    fun `beregning gir opphør hvis vilkår ikke er oppfylt`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.UFØRHET)
            }
        }
    }

    @Test
    fun `beregning gir ikke opphør hvis vilkår er oppfylt`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `beregningen gir ikke opphør dersom beløpet er under minstegrense, men endringen er mindre enn 10 prosent`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (_, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        arbeidsinntekt = 20535.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        arbeidsinntekt = 21735.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().beregn(
                eksisterendeUtbetalinger = listOf(lagUtbetaling(lagUtbetalingslinje(440, revurdering.periode))),
                clock = fixedClock,
            ).getOrFail().let {
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
                it.beregning.harAlleMånederMerknadForAvslag() shouldBe true
            }
        }
    }

    @Test
    fun `beregning med beløpsendring større enn 10 prosent fører til endring`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `beregning med beløpsendring mindre enn 10 prosent fører ikke til endring`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 1000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            }
        }
    }

    @Test
    fun `beregning med beløpsendring mindre enn 10 prosent fører til endring - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 1000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
            }
        }
    }

    @Test
    fun `beregning uten beløpsendring fører til ingen endring - g regulering`() {
        opprettetRevurdering(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe false
                it shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør - g regulering`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(), begrunnelse = "a",
            ),
        ).let { (sak, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = revurdering.periode,
                        arbeidsinntekt = 350_000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            }
        }
    }

    @Test
    fun `beregning som fører til beløp lik 0 gir opphør`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = revurdering.periode,
                        arbeidsinntekt = 350_000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            }
        }
    }

    @Test
    fun `beregning som fører til beløp under minstegrense gir opphør`() {
        opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().let { (sak, revurdering) ->
            revurdering.oppdaterFradragOgMarkerSomVurdert(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        arbeidsinntekt = (Sats.HØY.månedsbeløp(1.januar(2021)) - 250),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        arbeidsinntekt = (Sats.HØY.månedsbeløp(1.mai(2021)) - 250),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail().beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
            ).getOrFail().let {
                over10ProsentEndring(it.beregning, sak.utbetalinger) shouldBe true
                it shouldBe beOfType<BeregnetRevurdering.Opphørt>()
                (it as BeregnetRevurdering.Opphørt).utledOpphørsgrunner(fixedClock) shouldBe listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            }
        }
    }

    @Test
    fun `beregning med avkorting`() {
        val revurderinsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val avkortingPerMnd = 8000
        val expectedTotalAvkorting = revurderinsperiode.getAntallMåneder() * avkortingPerMnd
        val avkortingsgrunnlag = Grunnlag.Fradragsgrunnlag.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            fradrag = FradragFactory.ny(
                type = Fradragstype.AvkortingUtenlandsopphold,
                månedsbeløp = avkortingPerMnd.toDouble(),
                periode = revurderinsperiode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        opprettetRevurdering(
            revurderingsperiode = revurderinsperiode,
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                avkortingsgrunnlag = listOf(avkortingsgrunnlag),
            ).getOrFail().let { beregnet ->
                beregnet shouldBe beOfType<BeregnetRevurdering.Innvilget>()
                beregnet.beregning.getSumYtelse() shouldBeGreaterThan 0
                beregnet.beregning.getMånedsberegninger()[0].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[0].getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe 21989
                beregnet.beregning.getMånedsberegninger()[1].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[2].getSumYtelse() shouldBe
                    (3 * Sats.HØY.månedsbeløpSomHeltall(mai(2021).fraOgMed)) - expectedTotalAvkorting
                beregnet.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedTotalAvkorting

                beregnet.grunnlagsdata shouldBe revurdering.grunnlagsdata.copy(
                    fradragsgrunnlag = revurdering.grunnlagsdata.fradragsgrunnlag + listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[0].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.AvkortingUtenlandsopphold,
                                månedsbeløp = 21989.0,
                                periode = mai(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[1].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.AvkortingUtenlandsopphold,
                                månedsbeløp = 21989.0,
                                periode = juni(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[2].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.AvkortingUtenlandsopphold,
                                månedsbeløp = 20022.0,
                                periode = juli(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `tilpasser avkorting i forhold til andre fradrag`() {
        val revurderinsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val avkortingPerMnd = 8000
        val expectedTotalAvkorting = revurderinsperiode.getAntallMåneder() * avkortingPerMnd
        val arbeidsinntekt = avkortingPerMnd / 2

        opprettetRevurdering(
            revurderingsperiode = revurderinsperiode,
            grunnlagsdataOverrides = listOf(
                Grunnlag.Fradragsgrunnlag.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = arbeidsinntekt.toDouble(),
                        periode = revurderinsperiode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                avkortingsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = avkortingPerMnd.toDouble(),
                            periode = revurdering.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ).getOrFail().let { beregnet ->
                beregnet shouldBe beOfType<BeregnetRevurdering.Innvilget>()
                beregnet.beregning.getSumYtelse() shouldBeGreaterThan 0
                beregnet.beregning.getMånedsberegninger()[0].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[0].getFradrag()
                    .filter { it.fradragstype == Fradragstype.Arbeidsinntekt }
                    .sumOf { it.månedsbeløp } shouldBe 4000
                beregnet.beregning.getMånedsberegninger()[0].getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe 17989
                beregnet.beregning.getMånedsberegninger()[1].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[2].getSumYtelse() shouldBe 0
                beregnet.beregning.getMånedsberegninger()[3].getSumYtelse() shouldBe
                    (4 * Sats.HØY.månedsbeløpSomHeltall(mai(2021).fraOgMed)) - (4 * arbeidsinntekt) - expectedTotalAvkorting
                beregnet.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedTotalAvkorting

                beregnet.grunnlagsdata shouldBe revurdering.grunnlagsdata.copy(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[0].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.Arbeidsinntekt,
                                månedsbeløp = 4000.0,
                                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[1].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.AvkortingUtenlandsopphold,
                                månedsbeløp = 17989.0,
                                periode = mai(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[2].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.AvkortingUtenlandsopphold,
                                månedsbeløp = 17989.0,
                                periode = juni(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[3].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.AvkortingUtenlandsopphold,
                                månedsbeløp = 17989.0,
                                periode = juli(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        Grunnlag.Fradragsgrunnlag.create(
                            id = beregnet.grunnlagsdata.fradragsgrunnlag[4].id,
                            opprettet = Tidspunkt.now(fixedClock),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.AvkortingUtenlandsopphold,
                                månedsbeløp = 10033.0,
                                periode = august(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `får ikke lov til å opphøre pga andre vilkår dersom revurdering inneholder fremtidige fradrag for avkorting`() {
        opprettetRevurdering(
            vilkårOverrides = listOf(
                avslåttFormueVilkår(periode = periode2021),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                avkortingsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 15000.0,
                            periode = juni(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ) shouldBe Revurdering.KunneIkkeBeregneRevurdering.OpphørAvYtelseSomSkalAvkortes.left()
        }
    }

    @Test
    fun `får ikke lov til å opphøre pga høy inntekt dersom revurdering inneholder fremtidige fradrag for avkorting`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 25000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        ).let { (sak, revurdering) ->
            revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                avkortingsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 15000.0,
                            periode = juni(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ) shouldBe Revurdering.KunneIkkeBeregneRevurdering.OpphørAvYtelseSomSkalAvkortes.left()
        }
    }

    private fun lagUtbetaling(
        vararg utbetalingslinjer: Utbetalingslinje,
    ) = Utbetaling.OversendtUtbetaling.MedKvittering(
        opprettet = fixedTidspunkt,
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
        opprettet = fixedTidspunkt,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
        uføregrad = Uføregrad.parse(50),
    )

    private fun over10ProsentEndring(
        nyBeregning: Beregning,
        eksisterendeUtbetalinger: List<Utbetaling>,
    ): Boolean {
        val nyBeregningBeløp = nyBeregning.getSumYtelse()
        val eksisterendeBeløp = eksisterendeUtbetalinger.sumOf {
            TidslinjeForUtbetalinger(
                periode = nyBeregning.periode,
                utbetalingslinjer = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer },
                clock = fixedClock,
            ).tidslinje.sumOf { it.beløp * it.periode.getAntallMåneder() }
        }
        return abs((nyBeregningBeløp.toDouble() - eksisterendeBeløp) / eksisterendeBeløp * 100) > 10.0
    }
}
