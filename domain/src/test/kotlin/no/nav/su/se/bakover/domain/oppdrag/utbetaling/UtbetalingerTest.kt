package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.assertions.throwables.shouldThrowWithMessage
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetaling
import no.nav.su.se.bakover.test.utbetaling.utbetalinger
import no.nav.su.se.bakover.test.utbetaling.utbetalingerNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingerOpphør
import no.nav.su.se.bakover.test.utbetaling.utbetalingerReaktivering
import no.nav.su.se.bakover.test.utbetaling.utbetalingerStans
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeOpphørt
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeReaktivering
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeStans
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class UtbetalingerTest {

    @Nested
    inner class feilscenarier {

        @Test
        fun `kan ikke ha duplikate utbetalingsider`() {
            val clock = TikkendeKlokke()
            val utbetalingId = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c")
            val ny1 = oversendtUtbetalingMedKvittering(
                id = utbetalingId,
                clock = clock,
                eksisterendeUtbetalinger = utbetalingerNy(
                    id = utbetalingId,
                    clock = clock,
                ),
            )
            val ny2 = oversendtUtbetalingMedKvittering(
                id = utbetalingId,
                clock = clock,
                eksisterendeUtbetalinger = utbetalingerNy(
                    id = utbetalingId,
                    clock = clock,
                ),
            )
            shouldThrowWithMessage<IllegalStateException>("Kan ikke inneholde duplikate utbetalinger. Fant duplikater for: {7979ab18-578a-4877-b5ce-03aa9c=2}") {
                Utbetalinger(listOf(ny1, ny2))
            }
        }

        @Test
        fun `Første linje må være ny`() {
            val clock = TikkendeKlokke()
            shouldThrowWithMessage<IllegalStateException>("Den første utbetalingslinjen for en sak må være av typen Ny.") {
                Utbetalinger(
                    utbetaling(
                        clock = clock,
                        Utbetalingslinje.Endring.Opphør(
                            id = UUID30.randomUUID(),
                            opprettet = fixedTidspunkt,
                            rekkefølge = Rekkefølge.FØRSTE,
                            fraOgMed = 1.januar(2021),
                            tilOgMed = 31.januar(2021),
                            forrigeUtbetalingslinjeId = null,
                            beløp = 0,
                            virkningsperiode = januar(2021),
                            uføregrad = Uføregrad.parse(50),
                        ),
                    ),
                )
            }
        }

        @Test
        fun `Første linje kan ikke ha satt forrigeUtbetalingslinje`() {
            val clock = TikkendeKlokke()
            shouldThrowWithMessage<IllegalStateException>("Den første utbetalingslinjen kan ikke ha forrigeUtbetalingslinjeId satt.") {
                Utbetalinger(
                    utbetaling(
                        clock = clock,
                        utbetalingslinjeNy(
                            forrigeUtbetalingslinjeId = UUID30.randomUUID(),
                        ),
                    ),
                )
            }
        }

        @Test
        fun `kan ikke reaktivere et opphør`() {
            val clock = TikkendeKlokke()
            val id = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c")
            val opphør = utbetalingerOpphør(
                clock = clock,
                id = id,
            )
            val reaktivering = utbetaling(
                clock = clock,
                utbetalingslinjeReaktivering(
                    utbetalingslinjeSomSkalEndres = opphør.sisteUtbetalingslinje()!!,
                    clock = clock,
                    rekkefølge = Rekkefølge.FØRSTE,
                ),
            )
            shouldThrowWithMessage<IllegalStateException>("Oppdaget 3 av denne samme utbetalingslinjeIDen 7979ab18-578a-4877-b5ce-03aa9c med endringer etter et opphør: [Ny, Opphør, Reaktivering]") {
                opphør.plus(reaktivering)
            }
        }

        @Test
        fun `kan ikke reaktivere en reaktivering`() {
            val clock = TikkendeKlokke()
            val id = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c")
            val reaktivering1 = utbetalingerReaktivering(
                clock = clock,
                id = id,
            )
            val reaktivering2 = utbetaling(
                clock = clock,
                utbetalingslinjeReaktivering(
                    utbetalingslinjeSomSkalEndres = reaktivering1.sisteUtbetalingslinje()!!,
                    clock = clock,
                    rekkefølge = Rekkefølge.FØRSTE,
                ),
            )
            shouldThrowWithMessage<IllegalStateException>("Kan kun reaktivere en stans (Reaktivering(id=7979ab18-578a-4877-b5ce-03aa9c, opprettet=2021-01-01T01:02:06.456789Z, rekkefølge=Rekkefølge(value=2), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=null, beløp=5000, virkningsperiode=Periode(fraOgMed=2021-01-01, tilOgMed=2021-12-31), uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig)), men var: Reaktivering(id=7979ab18-578a-4877-b5ce-03aa9c, opprettet=2021-01-01T01:02:11.456789Z, rekkefølge=Rekkefølge(value=0), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=null, beløp=5000, virkningsperiode=Periode(fraOgMed=2021-01-01, tilOgMed=2021-12-31), uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig)") {
                reaktivering1.plus(reaktivering2)
            }
        }

        @Test
        fun `kan ikke reaktivere en ny`() {
            val clock = TikkendeKlokke()
            val nyId = UUID30.fromString("ea08e955-b629-42e2-b2b9-847e7a")

            val ny = utbetalingerNy(
                id = nyId,
                clock = clock,
            )
            val reaktivering = utbetaling(
                clock = clock,
                utbetalingslinjeReaktivering(
                    utbetalingslinjeSomSkalEndres = ny.sisteUtbetalingslinje()!!,
                    clock = clock,
                    rekkefølge = Rekkefølge.FØRSTE,
                ),
            )
            shouldThrowWithMessage<IllegalStateException>("Kan kun reaktivere en stans (Ny(id=ea08e955-b629-42e2-b2b9-847e7a, opprettet=2021-01-01T01:02:04.456789Z, rekkefølge=Rekkefølge(value=0), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=null, beløp=5000, uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig)), men var: Reaktivering(id=ea08e955-b629-42e2-b2b9-847e7a, opprettet=2021-01-01T01:02:09.456789Z, rekkefølge=Rekkefølge(value=0), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=null, beløp=5000, virkningsperiode=Periode(fraOgMed=2021-01-01, tilOgMed=2021-12-31), uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig)") {
                ny.plus(reaktivering)
            }
        }

        @Test
        fun `kan ikke stanse et opphør`() {
            val clock = TikkendeKlokke()
            val id = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c")
            val opphør = utbetalingerOpphør(
                id = id,
                clock = clock,
            )
            val stans = utbetaling(
                clock = clock,
                utbetalingslinjeStans(
                    utbetalingslinjeSomSkalEndres = opphør.sisteUtbetalingslinje()!!,
                    clock = clock,
                    rekkefølge = Rekkefølge.FØRSTE,
                ),
            )
            shouldThrowWithMessage<IllegalStateException>("Oppdaget 3 av denne samme utbetalingslinjeIDen 7979ab18-578a-4877-b5ce-03aa9c med endringer etter et opphør: [Ny, Opphør, Stans]") {
                opphør.plus(stans)
            }
        }

        @Test
        fun `kan ikke stanse en stans`() {
            val clock = TikkendeKlokke()
            val stans1Id = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c")
            val stans1 = utbetalingerStans(
                id = stans1Id,
                clock = clock,
            )
            val stans = utbetaling(
                clock = clock,
                utbetalingslinjeStans(
                    utbetalingslinjeSomSkalEndres = stans1.sisteUtbetalingslinje()!!,
                    clock = clock,
                    rekkefølge = Rekkefølge.FØRSTE,
                ),
            )
            shouldThrowWithMessage<IllegalStateException>("Kan ikke stanse (Stans(id=7979ab18-578a-4877-b5ce-03aa9c, opprettet=2021-01-01T01:02:10.456789Z, rekkefølge=Rekkefølge(value=0), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=null, beløp=5000, virkningsperiode=Periode(fraOgMed=2021-01-01, tilOgMed=2021-12-31), uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig)) en stans (Stans(id=7979ab18-578a-4877-b5ce-03aa9c, opprettet=2021-01-01T01:02:05.456789Z, rekkefølge=Rekkefølge(value=1), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=null, beløp=5000, virkningsperiode=Periode(fraOgMed=2021-01-01, tilOgMed=2021-12-31), uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig))") {
                stans1.plus(stans)
            }
        }

        @Test
        fun `kan kun opphøre forrige linje`() {
            val clock = TikkendeKlokke()
            val førstUtbetalingslinjeId = UUID30.fromString("7979ab18-578a-4877-b5ce-03aa9c")
            val førsteUtbetalingslinje = utbetalingslinjeNy(clock = clock, id = førstUtbetalingslinjeId)
            val andreUtbetalingslinjeId = UUID30.fromString("ea08e955-b629-42e2-b2b9-847e7a")
            val andreUtbetalingslinje = utbetalingslinjeNy(
                id = andreUtbetalingslinjeId,
                clock = clock,
                forrigeUtbetalingslinjeId = førstUtbetalingslinjeId,
                rekkefølge = Rekkefølge.ANDRE,
            )
            val førsteUtbetaling = utbetalinger(
                clock = clock,
                førsteUtbetalingslinje,
                andreUtbetalingslinje,
            )
            val opphør = utbetaling(
                clock = clock,
                utbetalingslinjeOpphørt(
                    utbetalingslinjeSomSkalEndres = førsteUtbetalingslinje,
                    clock = clock,
                    rekkefølge = Rekkefølge.FØRSTE,
                ),
            )
            shouldThrowWithMessage<IllegalStateException>("Kan kun endre forrige linje. forrige linje: Ny(id=ea08e955-b629-42e2-b2b9-847e7a, opprettet=2021-01-01T01:02:05.456789Z, rekkefølge=Rekkefølge(value=1), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=7979ab18-578a-4877-b5ce-03aa9c, beløp=15000, uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig), endring: Opphør(id=7979ab18-578a-4877-b5ce-03aa9c, opprettet=2021-01-01T01:02:10.456789Z, rekkefølge=Rekkefølge(value=0), fraOgMed=2021-01-01, tilOgMed=2021-12-31, forrigeUtbetalingslinjeId=null, beløp=15000, virkningsperiode=Periode(fraOgMed=2021-01-01, tilOgMed=2021-12-31), uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig)") {
                førsteUtbetaling.plus(opphør)
            }
        }
    }
}
