package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.utbetaling.kvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.simulertUtbetaling
import org.junit.jupiter.api.Test
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.simulering.lagUtbetalingMedKvitteringskontroll
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingskvitteringException

internal class UtbetalingskvitteringerTest {
    @Test
    fun `ingen tidligere utbetalinger krever ikke kvittering`() {
        val utbetalinger = Utbetalinger()

        utbetalinger.kastHvisIkkeAlleErKvitterte()
        utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
    }

    @Test
    fun `OK-kvittering med og uten varsel tillater ny utbetaling`() {
        listOf(Kvittering.Utbetalingsstatus.OK, Kvittering.Utbetalingsstatus.OK_MED_VARSEL).forEach { status ->
            val utbetalinger = Utbetalinger(oversendtUtbetalingMedKvittering(utbetalingsstatus = status))

            utbetalinger.kastHvisIkkeAlleErKvitterte()
            utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            lagUtbetalingMedKvitteringskontroll {
                utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            } shouldBe Unit.right()
        }
    }

    @Test
    fun `manglende kvittering kaster også ved korrigering`() {
        listOf(simulertUtbetaling(), oversendtUtbetalingUtenKvittering()).forEach { utbetaling ->
            val utbetalinger = Utbetalinger(utbetaling)
            val feil = Utbetalinger.Kvitteringsfeil.AvventerKvittering

            shouldThrow<UtbetalingskvitteringException> {
                utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            }.feil shouldBe feil
            shouldThrow<UtbetalingskvitteringException> {
                utbetalinger.kastHvisIkkeAlleErKvitterte()
            }.feil shouldBe feil
            lagUtbetalingMedKvitteringskontroll {
                utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            } shouldBe SimuleringFeilet.Utbetalingskvittering(feil).left()
        }
    }

    @Test
    fun `feilkvittering kaster uten eksplisitt korrigering`() {
        val utbetalinger = Utbetalinger(oversendtUtbetalingMedKvittering(utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL))
        val feil = Utbetalinger.Kvitteringsfeil.Feilkvittering

        shouldThrow<UtbetalingskvitteringException> {
            utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
        }.feil shouldBe feil
        lagUtbetalingMedKvitteringskontroll {
            utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
        } shouldBe SimuleringFeilet.Utbetalingskvittering(feil).left()
        utbetalinger.kastHvisIkkeAlleErKvitterte()
    }

    @Test
    fun `alle tidligere utbetalinger må ha OK-kvittering selv om siste har det`() {
        val clock = TikkendeKlokke()
        val første = oversendtUtbetalingUtenKvittering(clock = clock)
        val siste = oversendtUtbetalingMedKvittering(
            clock = clock,
            eksisterendeUtbetalinger = Utbetalinger(første.toKvittertUtbetaling(kvittering(clock = clock))),
        )
        val utbetalinger = Utbetalinger(første, siste)

        shouldThrow<UtbetalingskvitteringException> {
            utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
        }.feil shouldBe Utbetalinger.Kvitteringsfeil.AvventerKvittering
        val feilkvittert = første.toKvittertUtbetaling(kvittering(Kvittering.Utbetalingsstatus.FEIL, clock))
        shouldThrow<UtbetalingskvitteringException> {
            Utbetalinger(feilkvittert, siste).kastHvisIkkeAlleErKvitterteUtenFeil()
        }.feil shouldBe Utbetalinger.Kvitteringsfeil.Feilkvittering
    }

    @Test
    fun `manglende kvittering og feilkvittering bevares som to blokkerende forhold`() {
        val clock = TikkendeKlokke()
        val første = oversendtUtbetalingUtenKvittering(clock = clock)
        val siste = oversendtUtbetalingMedKvittering(
            clock = clock,
            eksisterendeUtbetalinger = Utbetalinger(første.toKvittertUtbetaling(kvittering(clock = clock))),
            utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
        )

        val utbetalinger = Utbetalinger(første, siste)
        val feil = Utbetalinger.Kvitteringsfeil.FeilkvitteringOgAvventerKvittering

        shouldThrow<UtbetalingskvitteringException> {
            utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
        }.feil shouldBe feil
        lagUtbetalingMedKvitteringskontroll {
            utbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
        } shouldBe SimuleringFeilet.Utbetalingskvittering(feil).left()
        shouldThrow<UtbetalingskvitteringException> {
            utbetalinger.kastHvisIkkeAlleErKvitterte()
        }.feil shouldBe Utbetalinger.Kvitteringsfeil.AvventerKvittering
    }

    @Test
    fun `andre exceptions skal ikke oversettes til kvitteringsfeil`() {
        val feil = IllegalStateException("En annen invariant er brutt")

        shouldThrow<IllegalStateException> {
            lagUtbetalingMedKvitteringskontroll { throw feil }
        } shouldBe feil
    }
}
