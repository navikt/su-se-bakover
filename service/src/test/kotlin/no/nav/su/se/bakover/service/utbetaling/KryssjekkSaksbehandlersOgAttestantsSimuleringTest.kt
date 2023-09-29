package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkSaksbehandlersOgAttestantsSimulering
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.utbetaling.nyUtbetalingSimulert
import org.junit.jupiter.api.Test

class KryssjekkSaksbehandlersOgAttestantsSimuleringTest {
    @Test
    fun `kontroll av simulering happy path`() {
        val clock = TikkendeKlokke()
        val (sak, revurdering) = simulertRevurdering(clock = clock)

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            beregning = revurdering.beregning,
            clock = clock,
        )

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk().shouldBeRight()
    }

    @Test
    fun `kontroll av simulering - endring i gjelder id`() {
        val clock = TikkendeKlokke()
        val (sak, revurdering) = simulertRevurdering(clock = clock)

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            beregning = revurdering.beregning,
            clock = clock,
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
        val clock = TikkendeKlokke()
        val (sak, revurdering) = simulertRevurdering(clock = clock)

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            beregning = revurdering.beregning,
            clock = clock,
        ).copy(
            simulering = simuleringFeilutbetaling(
                gjelderId = sak.fnr,
                perioder = revurdering.periode.måneder().toTypedArray(),
            ),
        )

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk() shouldBe KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling.left()
    }

    @Test
    fun `kontroll av simulering - saksbehandler er tom men ikke attestants`() {
        val clock = TikkendeKlokke()
        val (sak, revurdering) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = clock,
        )

        val saksbehandlerSimulering = revurdering.simulering
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak to revurdering,
            // lager simulering for søknadsbehandlingen
            beregning = (sak.søknadsbehandlinger.first() as IverksattSøknadsbehandling.Innvilget).beregning,
            clock = clock,
        )

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk() shouldBe KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikPeriode.left()
    }

    @Test
    fun `kontroll av simulering -  ulike beløp`() {
        val clock = TikkendeKlokke()
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
            clock = clock,
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
            clock = clock,
        )

        val saksbehandlerSimulering =
            (søknadsbehandling as VedtakInnvilgetSøknadsbehandling).simulering // bruker simulering fra søknadsbehandling
        val attestantSimulertUtbetaling = nyUtbetalingSimulert(
            sakOgBehandling = sak2 to revurdering,
            beregning = revurdering.beregning,
            clock = clock,
        )

        KryssjekkSaksbehandlersOgAttestantsSimulering(
            saksbehandlersSimulering = saksbehandlerSimulering,
            attestantsSimulering = attestantSimulertUtbetaling,
        ).sjekk() shouldBe KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UliktBeløp.left()
    }
}
