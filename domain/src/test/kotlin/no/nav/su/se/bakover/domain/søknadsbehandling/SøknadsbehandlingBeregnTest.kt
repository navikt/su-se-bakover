package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.simuleringOpphørt
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

internal class SøknadsbehandlingBeregnTest {
    @Test
    fun `beregner uten avkorting`() {
        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, søknadsbehandling) ->
            søknadsbehandling.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
            ).getOrFail().let { beregnet ->
                beregnet.beregning.getFradrag() shouldHaveSize 1
                beregnet.beregning.getSumFradrag() shouldBe 0.0
                beregnet.beregning.getSumYtelse() shouldBe søknadsbehandling.periode.tilMånedsperioder()
                    .sumOf { Sats.HØY.månedsbeløpSomHeltall(it.fraOgMed) }
                beregnet.beregning.getBegrunnelse() shouldBe "kakota"
            }
        }
    }

    @Test
    fun `beregner med fradrag`() {
        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, søknadsbehandling) ->
            søknadsbehandling.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 15000.0,
                            periode = søknadsbehandling.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ).getOrFail().beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
            ).getOrFail().let { beregnet ->
                beregnet.beregning.getFradrag() shouldHaveSize 2
                beregnet.beregning.getSumFradrag() shouldBe søknadsbehandling.periode.getAntallMåneder() * 15000
                beregnet.beregning.getSumYtelse() shouldBe søknadsbehandling.periode.tilMånedsperioder()
                    .sumOf { Sats.HØY.månedsbeløpSomHeltall(it.fraOgMed) - 15000 }
            }
        }
    }

    @Test
    fun `beregner med avkorting`() {
        val antallMånederMedFeilutbetaling = 3L
        val eksisterendeUtbetalinger = listOf(oversendtUtbetalingMedKvittering())
        val expectedAvkortingBeløp = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer }
            .sumOf { it.beløp } * antallMånederMedFeilutbetaling.toDouble()

        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, søknadsbehandling) ->
            søknadsbehandling.copy(
                avkorting = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    sakId = søknadsbehandling.id,
                    revurderingId = UUID.randomUUID(),
                    opprettet = søknadsbehandling.opprettet,
                    simulering = simuleringOpphørt(
                        opphørsdato = LocalDate.now(nåtidForSimuleringStub).startOfMonth()
                            .minusMonths(antallMånederMedFeilutbetaling),
                        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                        fnr = søknadsbehandling.fnr,
                        sakId = søknadsbehandling.sakId,
                        saksnummer = søknadsbehandling.saksnummer,
                        clock = fixedClock,
                    ),
                ).skalAvkortes(),
            ).beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
            ).getOrFail().let { beregnet ->
                beregnet.beregning.getFradrag() shouldHaveSize 4
                beregnet.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldHaveSize 3
                beregnet.beregning.getSumFradrag() shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
                beregnet.beregning.getSumYtelse() shouldBe søknadsbehandling.periode.tilMånedsperioder()
                    .sumOf { Sats.HØY.månedsbeløpSomHeltall(it.fraOgMed) } - expectedAvkortingBeløp
            }
        }
    }

    @Test
    fun `kaster exception hvis avkorting er i ugyldig tilstand`() {
        assertThrows<IllegalStateException> {
            søknadsbehandlingVilkårsvurdertInnvilget().let { (_, søknadsbehandling) ->
                søknadsbehandling.copy(
                    avkorting = mock<Avkortingsvarsel.Utenlandsopphold.Opprettet>(),
                ).beregn(
                    begrunnelse = "kakota",
                    clock = fixedClock,
                )
            }
        }

        assertThrows<IllegalStateException> {
            søknadsbehandlingVilkårsvurdertInnvilget().let { (_, søknadsbehandling) ->
                søknadsbehandling.copy(
                    avkorting = mock<Avkortingsvarsel.Utenlandsopphold.Avkortet>(),
                ).beregn(
                    begrunnelse = "kakota",
                    clock = fixedClock,
                )
            }
        }
    }
}
