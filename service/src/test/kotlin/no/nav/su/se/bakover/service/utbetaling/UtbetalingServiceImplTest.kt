package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.utbetaling.kvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import økonomi.application.utbetaling.FantIkkeUtbetaling
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje

internal class UtbetalingServiceImplTest {

    @Test
    fun `oppdater med kvittering - ikke funnet`() {
        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentOversendtUtbetalingForAvstemmingsnøkkel(any<Avstemmingsnøkkel>()) } doReturn null
            },
        ).let {
            it.service.oppdaterMedKvittering(
                utbetalingId = UUID30.randomUUID(),
                kvittering = kvittering(),
                sessionContext = null,
            ) shouldBe FantIkkeUtbetaling.left()
        }
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val utbetalingUtenKvittering = oversendtUtbetalingUtenKvittering()
        val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(kvittering())

        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentOversendtUtbetalingForUtbetalingId(any<UUID30>(), anyOrNull()) } doReturn utbetalingUtenKvittering
            },
        ).let {
            it.service.oppdaterMedKvittering(
                utbetalingId = utbetalingUtenKvittering.id,
                kvittering = kvittering(),
                sessionContext = null,
            ).getOrFail() shouldBe utbetalingMedKvittering

            verify(it.utbetalingRepo).oppdaterMedKvittering(utbetalingMedKvittering, null)
        }
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val utbetalingMedKvittering = oversendtUtbetalingMedKvittering()

        UtbetalingServiceAndMocks(
            utbetalingRepo = mock {
                on { hentOversendtUtbetalingForUtbetalingId(any<UUID30>(), anyOrNull()) } doReturn utbetalingMedKvittering
            },
        ).let {
            it.service.oppdaterMedKvittering(
                utbetalingId = utbetalingMedKvittering.id,
                kvittering = kvittering(),
                sessionContext = null,
            ).getOrFail() shouldBe utbetalingMedKvittering

            verify(it.utbetalingRepo, never()).oppdaterMedKvittering(any(), anyOrNull())
        }
    }

    @Nested
    inner class hentGjeldendeUtbetaling {
        @Test
        fun `henter utbetalingslinjen som gjelder for datoen som sendes inn`() {
            val (sak, _) = iverksattSøknadsbehandlingUføre(
                customGrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = januar(2021),
                        arbeidsinntekt = 1000.0,
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = februar(2021),
                        arbeidsinntekt = 2000.0,
                    ),
                    fradragsgrunnlagArbeidsinntekt(
                        periode = mars(2021),
                        arbeidsinntekt = 3000.0,
                    ),
                ),
            )

            UtbetalingServiceAndMocks(
                utbetalingRepo = mock {
                    on { hentOversendteUtbetalinger(any(), any()) } doReturn sak.utbetalinger
                },
            ).also {
                it.service.hentGjeldendeUtbetaling(
                    sakId = sak.id,
                    forDato = 15.januar(2021),
                ) shouldBe UtbetalingslinjePåTidslinje.Ny(
                    kopiertFraId = sak.utbetalinger.first().utbetalingslinjer[0].id,
                    periode = januar(2021),
                    beløp = 19946,
                ).right()

                it.service.hentGjeldendeUtbetaling(
                    sakId = sak.id,
                    forDato = 29.mars(2021),
                ) shouldBe UtbetalingslinjePåTidslinje.Ny(
                    kopiertFraId = sak.utbetalinger.first().utbetalingslinjer[2].id,
                    periode = mars(2021),
                    beløp = 17946,
                ).right()
            }
        }
    }
}
