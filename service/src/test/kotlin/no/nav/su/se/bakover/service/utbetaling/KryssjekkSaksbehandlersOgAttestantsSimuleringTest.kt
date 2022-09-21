package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkSaksbehandlersOgAttestantsSimulering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test

class KryssjekkSaksbehandlersOgAttestantsSimuleringTest {
    @Test
    fun `kontroll av simulering happy path`() {
        val (sak, revurdering) = simulertRevurdering()

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            beregning = revurdering.beregning,
            clock = fixedClock,
        )

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk().shouldBeRight()
    }

    @Test
    fun `kontroll av simulering - endring i gjelder id`() {
        val (sak, revurdering) = simulertRevurdering()

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            beregning = revurdering.beregning,
            clock = fixedClock,
        ).let {
            it.copy(
                simulering = it.simulering.copy(
                    gjelderId = Fnr.generer(),
                ),
            )
        }

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk() shouldBe KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikGjelderId.left()
    }

    @Test
    fun `kontroll av simulering - feilutbetaling`() {
        val (sak, revurdering) = simulertRevurdering()

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            beregning = revurdering.beregning,
            clock = fixedClock,
        ).let {
            it.copy(
                simulering = simuleringFeilutbetaling(
                    perioder = revurdering.periode.måneder().toTypedArray(),
                ),
            )
        }

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk() shouldBe KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling.left()
    }

    @Test
    fun `kontroll av simulering - saksbehandler er tom men ikke attestants`() {
        val (sak, revurdering) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            beregning = (sak.søknadsbehandlinger.first() as Søknadsbehandling.Iverksatt.Innvilget).beregning, // lager simulering for søknadsbehandlingen
            clock = fixedClock,
        )

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk() shouldBe KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikPeriode.left()
    }

    @Test
    fun `kontroll av simulering -  ulike beløp`() {
        val (sak, _, søknadsbehandling) = iverksattSøknadsbehandlingUføre(
            customGrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val (sak2, revurdering) = simulertRevurdering(
            sakOgVedtakSomKanRevurderes = sak to søknadsbehandling as VedtakSomKanRevurderes,
            grunnlagsdataOverrides = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = august(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val saksbehandlerSimulering = (søknadsbehandling as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling).simulering // bruker simulering fra søknadsbehandling
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak2 to revurdering,
            beregning = revurdering.beregning,
            clock = fixedClock,
        )

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk() shouldBe KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp.left()
    }
}
