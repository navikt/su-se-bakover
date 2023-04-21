package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.tidslinje
import no.nav.su.se.bakover.test.utbetaling.utbetalinger
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeOpphørt
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class TidslinjeForUtbetalingerTest {

    @Nested
    inner class `Feilscenarier ved bruk av utbetalingslinjer direkte` {
        @Test
        fun `exception dersom duplikat rekkefølge`() {
            val clock = TikkendeKlokke()
            val første = utbetalingslinjeNy(
                clock = clock,
            )
            val andre = utbetalingslinjeNy(
                clock = clock,
                forrigeUtbetalingslinjeId = første.id,
            )
            shouldThrowWithMessage<IllegalStateException>("Kan ikke sammenligne linjer med samme rekkefølge.") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, andre))
            }
        }

        @Test
        fun `exception dersom feil rekkefølge`() {
            val clock = TikkendeKlokke()
            val første = utbetalingslinjeNy(
                clock = clock,
                rekkefølge = Rekkefølge.FØRSTE,

            )
            val andre = utbetalingslinjeNy(
                clock = clock,
                forrigeUtbetalingslinjeId = første.id,
                rekkefølge = Rekkefølge.ANDRE,

            )
            shouldThrowWithMessage<IllegalStateException>("Utbetalingslinjer er ikke sortert i stigende rekkefølge") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, andre, første))
            }
        }

        @Test
        fun `exception dersom første element ikke har start-rekkefølgen`() {
            val clock = TikkendeKlokke()
            val første = utbetalingslinjeNy(
                clock = clock,
                rekkefølge = Rekkefølge.ANDRE,
            )
            shouldThrowWithMessage<IllegalStateException>("Første linje må være Rekkefølge.start() som er: Rekkefølge(value=0), men var Rekkefølge(value=1)") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første))
            }
        }

        @Test
        fun `exception dersom ikke-sammenhengende rekkefølge`() {
            val clock = TikkendeKlokke()
            val første = utbetalingslinjeNy(
                clock = clock,
                rekkefølge = Rekkefølge.FØRSTE,

            )
            val andre = utbetalingslinjeNy(
                clock = clock,
                forrigeUtbetalingslinjeId = første.id,
                rekkefølge = Rekkefølge.TREDJE,

            )
            shouldThrowWithMessage<IllegalStateException>("Krever at rekkefølgen har en gitt start og er kontinuerlig, men var: [0, 2]") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, andre))
            }
        }

        @Test
        fun `exception dersom nye linjer har samme id`() {
            val clock = TikkendeKlokke()
            val id = UUID30.fromString("b4693e47-3f5d-48df-9e8c-f5c604")
            val første = utbetalingslinjeNy(
                id = id,
                clock = clock,
                rekkefølge = Rekkefølge.FØRSTE,

            )
            val andre = utbetalingslinjeNy(
                id = id,
                clock = clock,
                forrigeUtbetalingslinjeId = UUID30.randomUUID(), // id og forrige kan ikke være lik
                rekkefølge = Rekkefølge.ANDRE,

            )
            shouldThrowWithMessage<IllegalStateException>("Alle nye utbetalingslinjer skal ha forskjellig id, men var: [b4693e47-3f5d-48df-9e8c-f5c604, b4693e47-3f5d-48df-9e8c-f5c604]") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, andre))
            }
        }

        @Test
        fun `exception dersom nye linjer har samme forrigeUtbetalingId`() {
            val clock = TikkendeKlokke()
            val forrigeUtbetalingslinjeId = UUID30.fromString("b4693e47-3f5d-48df-9e8c-f5c604")
            val første = utbetalingslinjeNy(
                clock = clock,
                rekkefølge = Rekkefølge.FØRSTE,
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            )
            val andre = utbetalingslinjeNy(
                clock = clock,
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                rekkefølge = Rekkefølge.ANDRE,

            )
            shouldThrowWithMessage<IllegalStateException>("Alle nye utbetalingslinjer skal referere til forskjellig forrige utbetalingid, men var: [b4693e47-3f5d-48df-9e8c-f5c604, b4693e47-3f5d-48df-9e8c-f5c604]") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, andre))
            }
        }

        @Test
        fun `exception dersom endring ikke har samme forrigeUtbetalingslinjeId`() {
            val clock = TikkendeKlokke()
            val førsteId = UUID30.fromString("b7cb6456-9f7f-4396-ac3f-b958e4")
            val forrigeUtbetalingslinjeId = UUID30.fromString("b4693e47-3f5d-48df-9e8c-f5c604")
            val første = utbetalingslinjeNy(
                id = førsteId,
                clock = clock,
            )
            val andre = Utbetalingslinje.Endring.Opphør(
                id = førsteId,
                opprettet = Tidspunkt.now(clock),
                rekkefølge = Rekkefølge.ANDRE,
                fraOgMed = første.originalFraOgMed(),
                tilOgMed = første.originalTilOgMed(),
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                beløp = første.beløp,
                virkningsperiode = første.periode,
                uføregrad = første.uføregrad,

            )
            shouldThrowWithMessage<IllegalStateException>("To utbetalingslinjer med samme id, må også ha samme forrigeUtbetalingslinjeId. ID: b7cb6456-9f7f-4396-ac3f-b958e4, forrigeUtbetalingslinjeIDer: [null, b4693e47-3f5d-48df-9e8c-f5c604]") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, andre))
            }
        }

        @Test
        fun `exception dersom endring ikke har samme id`() {
            val clock = TikkendeKlokke()
            val id1 = UUID30.fromString("b7cb6456-9f7f-4396-ac3f-b958e4")
            val id2 = UUID30.fromString("a20d689e-e30a-48b6-9c00-1fb7be")
            val forrigeUtbetalingslinjeId = UUID30.fromString("b4693e47-3f5d-48df-9e8c-f5c604")
            val første = utbetalingslinjeNy(
                id = id1,
                clock = clock,
                rekkefølge = Rekkefølge.FØRSTE,
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            )
            val andre = Utbetalingslinje.Endring.Opphør(
                id = id2,
                opprettet = Tidspunkt.now(clock),
                rekkefølge = Rekkefølge.ANDRE,
                fraOgMed = første.originalFraOgMed(),
                tilOgMed = første.originalTilOgMed(),
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                beløp = første.beløp,
                virkningsperiode = første.periode,
                uføregrad = første.uføregrad,
            )
            shouldThrowWithMessage<IllegalStateException>("To utbetalingslinjer med samme forrigeUtbetalingslinjeId, må også ha samme id. IDer: [b7cb6456-9f7f-4396-ac3f-b958e4, a20d689e-e30a-48b6-9c00-1fb7be], forrigeUtbetalingslinjeID: b4693e47-3f5d-48df-9e8c-f5c604") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, andre))
            }
        }

        @Test
        fun `en ny linje må peke på forrige ny-linje sin ny`() {
            val clock = TikkendeKlokke()
            val forrigeUtbetalingslinjeId = UUID30.fromString("b4693e47-3f5d-48df-9e8c-f5c604")
            val id1 = UUID30.fromString("b7cb6456-9f7f-4396-ac3f-b958e4")
            val id2 = UUID30.fromString("a20d689e-e30a-48b6-9c00-1fb7be")
            val første = utbetalingslinjeNy(
                id = id1,
                clock = clock,
                rekkefølge = Rekkefølge.FØRSTE,
                forrigeUtbetalingslinjeId = null,
            )
            val andre = utbetalingslinjeNy(
                id = id2,
                clock = clock,
                forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                rekkefølge = Rekkefølge.ANDRE,

            )
            shouldThrowWithMessage<IllegalStateException>("En ny utbetalingslinje (id: a20d689e-e30a-48b6-9c00-1fb7be) sin forrigeUtbetalingslinjeId b4693e47-3f5d-48df-9e8c-f5c604 må peke på den forrige utbetalingslinjen (id: b7cb6456-9f7f-4396-ac3f-b958e4") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, andre))
            }
        }

        @Test
        fun `en ny linje må peke på forrige endr-linje sin id`() {
            val clock = TikkendeKlokke()
            val førsteNy = utbetalingslinjeNy(
                clock = clock,
            )
            val opphør = utbetalingslinjeOpphørt(
                utbetalingslinjeSomSkalEndres = førsteNy,
                clock = clock,
            )
            val andreNy = utbetalingslinjeNy(
                clock = clock,
                forrigeUtbetalingslinjeId = UUID30.randomUUID(),
                rekkefølge = Rekkefølge.TREDJE,

            )
            shouldThrowWithMessage<IllegalStateException>("En ny utbetalingslinje (id: ${andreNy.id}) sin forrigeUtbetalingslinjeId ${andreNy.forrigeUtbetalingslinjeId} må peke på den forrige utbetalingslinjen (id: ${opphør.id}") {
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, førsteNy, opphør, andreNy))
            }
        }
    }

    @Test
    fun `kan ha 2 endr linjer med samme id og samme forrigeUtbetalingslinjeId`() {
        val clock = TikkendeKlokke()
        val første = utbetalingslinjeNy(
            clock = clock,
        )
        val førsteOpphør = utbetalingslinjeOpphørt(
            utbetalingslinjeSomSkalEndres = første,
            clock = clock,
        )
        val andreOpphør = utbetalingslinjeOpphørt(
            utbetalingslinjeSomSkalEndres = førsteOpphør,
            clock = clock,
        )
        TidslinjeForUtbetalinger.fra(utbetalinger(clock, første, førsteOpphør, andreOpphør)) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = første.id,
                periode = år(2021),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `en utbetaling`() {
        val clock = TikkendeKlokke()
        val første = utbetalingslinjeNy(
            periode = januar(2020)..april(2020),
            beløp = 1000,
            clock = clock,
        )
        TidslinjeForUtbetalinger.fra(
            utbetaling = oversendtUtbetalingUtenKvittering(
                utbetalingslinjer = nonEmptyListOf(
                    første,
                ),
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
        )
    }

    @Test
    fun `et par helt ordinære utbetalinger`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val rekkefølge = Rekkefølge.generator()
        val første = utbetalingslinjeNy(
            opprettet = førsteTidspunkt,
            periode = januar(2020)..april(2020),
            beløp = 1000,
            rekkefølge = rekkefølge.neste(),
        )
        val andre = utbetalingslinjeNy(
            opprettet = førsteTidspunkt,
            periode = mai(2020)..desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,

                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
        )
    }

    @Test
    fun `enkel stans på tvers av måneder med forskjellig beløp`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)

        val rekkefølge = Rekkefølge.generator()
        val første = utbetalingslinjeNy(
            opprettet = førsteTidspunkt,
            periode = januar(2020)..april(2020),
            beløp = 1000,
            rekkefølge = rekkefølge.neste(),
        )
        val andre = utbetalingslinjeNy(
            opprettet = førsteTidspunkt,
            periode = mai(2020)..desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            rekkefølge = rekkefølge.neste(),
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = andre,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                stans,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = Periode.create(
                    1.januar(2020),
                    31.mars(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = stans.id,
                periode = Periode.create(
                    1.april(2020),
                    31.desember(2020),
                ),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `reaktivering av enkel stans på tvers av måneder med forskjellig beløp`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = stans,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                stans,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = januar(2020)..februar(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = stans.id,
                periode = mars(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,

                periode = april(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
                periode = mai(2020)..desember(2020),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `opphør av alle utbetalingene`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)

        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinjeSomSkalEndres = andre,
            virkningsperiode = Periode.create(
                1.januar(2020),
                andre.periode.tilOgMed,
            ),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                opphør,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = opphør.id,
                periode = år(2020),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `reaktivering av ytelse som har blitt revurdert etter stans`() {
        val clock = TikkendeKlokke()

        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val tredje = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.mars(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = førsteStans.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = tredje,
            virkningstidspunkt = 1.oktober(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = andreStans,
            virkningstidspunkt = 1.november(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                førsteStans,
                tredje,
                andreStans,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = Periode.create(
                    1.januar(2020),
                    29.februar(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,

                periode = Periode.create(
                    1.mars(2020),
                    30.september(2020),
                ),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                periode = oktober(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
                periode = Periode.create(
                    1.november(2020),
                    31.desember(2020),
                ),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `kan stanse tidligere reaktivert ytelse igjen`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            rekkefølge = rekkefølge.neste(),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = andre,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = førsteStans,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = reaktivering,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                førsteStans,
                reaktivering,
                andreStans,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = Periode.create(
                    1.januar(2020),
                    31.mars(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                periode = Periode.create(
                    1.april(2020),
                    31.desember(2020),
                ),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `regenerert informasjon får samme opprettettidspunkt som ferskere informasjon, men perioden overlapper ikke`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)

        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = andre,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val (reaktivering, reaktiveringOpprettet) = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = førsteStans,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        ).let {
            it to it.opprettet
        }
        val tredje = Utbetalingslinje.Ny(
            opprettet = reaktiveringOpprettet.plusUnits(1),
            fraOgMed = 1.januar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = reaktivering.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                førsteStans,
                reaktivering,
                tredje,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = Periode.create(
                    1.januar(2020),
                    31.mars(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,

                periode = april(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,

                periode = år(2021),
                beløp = tredje.beløp,
            ),
        )
    }

    @Test
    fun `reaktivering av tidligere stans og reaktiveringer på tvers av måneder med forskjellig beløp`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)

        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val førsteReaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = førsteStans,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = førsteReaktivering,
            virkningstidspunkt = 1.oktober(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val andreReaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = andreStans,
            virkningstidspunkt = 1.november(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                førsteStans,
                førsteReaktivering,
                andreStans,
                andreReaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = Periode.create(
                    1.januar(2020),
                    29.februar(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                periode = Periode.create(
                    1.mars(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                periode = Periode.create(
                    1.mai(2020),
                    30.september(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                periode = oktober(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andreReaktivering.id,
                periode = Periode.create(
                    1.november(2020),
                    31.desember(2020),
                ),
                beløp = andreReaktivering.beløp,
            ),
        )
    }

    @Test
    fun `blanding av alle utbetalingstypene`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)

        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinjeSomSkalEndres = andre,
            virkningsperiode = oktober(2020)..desember(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val tredje = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.oktober(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = opphør.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = tredje,
            virkningstidspunkt = 1.august(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = stans,
            virkningstidspunkt = 1.august(2020),
            clock = clock,
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                opphør,
                tredje,
                stans,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                periode = januar(2020)..april(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,
                periode = mai(2020)..juli(2020),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                periode = august(2020)..september(2020),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = tredje.id,
                periode = oktober(2020)..desember(2020),
                beløp = tredje.beløp,
            ),
        )
    }

    @Test
    fun `5 nye utbetalingslinjer, delvis overlappende`() {
        val clock = TikkendeKlokke()
        val rekkefølge = Rekkefølge.generator()
        val første = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val tredje = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = andre.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val fjerde = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.februar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = tredje.id,
            beløp = 4000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        val femte = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.mars(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = fjerde.id,
            beløp = 5000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )

        TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                første,
                andre,
                tredje,
                fjerde,
                femte,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,

                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,

                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,

                periode = januar(2021),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = fjerde.id,

                periode = februar(2021),
                beløp = fjerde.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = femte.id,

                periode = Periode.create(
                    1.mars(2021),
                    31.desember(2021),
                ),
                beløp = femte.beløp,
            ),
        )
    }

    @Test
    fun `tidslinje er evkvialent med seg selv `() {
        val clock = TikkendeKlokke()

        val rekkefølge = Rekkefølge.generator()
        val ny = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = rekkefølge.neste(),
        )
        listOf(ny).tidslinje().ekvivalentMed(listOf(ny).tidslinje()) shouldBe true

        listOf(
            ny,
            ny.copy(
                opprettet = ny.opprettet.plusUnits(1),
                rekkefølge = Rekkefølge.ANDRE,
                forrigeUtbetalingslinjeId = ny.id,
                id = UUID30.randomUUID(),
            ),
        ).tidslinje().ekvivalentMed(
            listOf(
                ny,
                ny.copy(
                    opprettet = ny.opprettet.plusUnits(1),
                    rekkefølge = Rekkefølge.ANDRE,
                    id = UUID30.randomUUID(),
                    forrigeUtbetalingslinjeId = ny.id,
                ),
            ).tidslinje(),
        ) shouldBe true

        listOf(ny).tidslinje().ekvivalentMed(
            listOf(
                ny,
                ny.copy(
                    opprettet = ny.opprettet.plusUnits(1),
                    rekkefølge = Rekkefølge.ANDRE,
                    id = UUID30.randomUUID(),
                    forrigeUtbetalingslinjeId = ny.id,
                ),
            ).tidslinje(),
        ) shouldBe true
    }

    @Test
    fun `tidslinje er evkvialent - lik periode og beløp `() {
        val clock = TikkendeKlokke()

        val a = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = Rekkefølge.start(),
        )
        val b = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(100),
            rekkefølge = Rekkefølge.start(),
        )
        listOf(a).tidslinje().ekvivalentMed(listOf(b).tidslinje()) shouldBe true
    }

    @Test
    fun `tidslinje er ikke evkvialent - forskjellig periode og beløp`() {
        val clock = TikkendeKlokke()

        val a = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),

            rekkefølge = Rekkefølge.start(),
        )
        val b = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.februar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(100),
            rekkefølge = Rekkefølge.start(),
        )
        val bb = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 5000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = Rekkefølge.start(),
        )
        listOf(a).tidslinje().ekvivalentMed(listOf(b).tidslinje()) shouldBe false
        listOf(a).tidslinje().ekvivalentMed(listOf(bb).tidslinje()) shouldBe false
    }

    @Test
    fun `rest kombinasjoner`() {
        val clock = TikkendeKlokke()

        val a = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = Rekkefølge.start(),
        )
        val b = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = Rekkefølge.start(),
        )
        val c = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 5000,
            uføregrad = Uføregrad.parse(50),
            rekkefølge = Rekkefølge.start(),
        )
        listOf(a).tidslinje().ekvivalentMed(listOf(b).tidslinje()) shouldBe true
        listOf(b).tidslinje().ekvivalentMed(listOf(a).tidslinje()) shouldBe true

        listOf(a).tidslinje().ekvivalentMed(listOf(c).tidslinje()) shouldBe false
        listOf(c).tidslinje().ekvivalentMed(listOf(a).tidslinje()) shouldBe false

        listOf(b).tidslinje().ekvivalentMed(listOf(c).tidslinje()) shouldBe false
        listOf(c).tidslinje().ekvivalentMed(listOf(b).tidslinje()) shouldBe false

        listOf(
            a,
            b.copy(forrigeUtbetalingslinjeId = a.id, rekkefølge = Rekkefølge.skip(0)),
        ).tidslinje().ekvivalentMed(listOf(c).tidslinje()) shouldBe false
        listOf(c).tidslinje().ekvivalentMed(
            listOf(
                a,
                b.copy(forrigeUtbetalingslinjeId = a.id, rekkefølge = Rekkefølge.skip(0)),
            ).tidslinje(),
        ) shouldBe false

        listOf(b, c.copy(forrigeUtbetalingslinjeId = b.id, rekkefølge = Rekkefølge.skip(0))).tidslinje()
            .ekvivalentMed(listOf(a).tidslinje()) shouldBe false
        listOf(a).tidslinje().ekvivalentMed(
            listOf(
                b,
                c.copy(forrigeUtbetalingslinjeId = b.id, rekkefølge = Rekkefølge.skip(0)),
            ).tidslinje(),
        ) shouldBe false

        listOf(a, c.copy(forrigeUtbetalingslinjeId = a.id, rekkefølge = Rekkefølge.skip(0))).tidslinje()
            .ekvivalentMed(listOf(b).tidslinje()) shouldBe false
        listOf(b).tidslinje().ekvivalentMed(
            listOf(
                a,
                c.copy(forrigeUtbetalingslinjeId = a.id, rekkefølge = Rekkefølge.skip(0)),
            ).tidslinje(),
        ) shouldBe false
    }

    @Test
    fun `periode som er innenfor tidslinjensperiode blir krympet ok`() {
        val clock = TikkendeKlokke()
        TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy()))!!.let {
            it.periode shouldBe år(2021)
            it.krympTilPeriode(mai(2021)..november(2021))!!.ekvivalentMed(
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy(periode = mai(2021)..november(2021))))!!,
            ) shouldBe true
        }

        TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy()))!!.let {
            it.periode shouldBe år(2021)
            it.krympTilPeriode(1.mai(2021))!!.ekvivalentMed(
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy(periode = mai(2021)..desember(2021))))!!,
            ) shouldBe true
        }
    }

    @Test
    fun `tidslinjens periode er ok dersom man kryper til en periode som er større`() {
        val clock = TikkendeKlokke()
        val expectedPeriode = mai(2021)..desember(2021)
        TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy(periode = expectedPeriode)))!!.let {
            it.periode shouldBe expectedPeriode
            it.krympTilPeriode(år(2021))!!.ekvivalentMed(
                TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy(periode = expectedPeriode)))!!,
            ) shouldBe true
        }
    }

    @Test
    fun `gir null dersom perioden som skal bli krympet til, ikke er i tidslinjens periode`() {
        val clock = TikkendeKlokke()
        TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy()))!!.let {
            it.periode shouldBe år(2021)
            it.krympTilPeriode(mai(2022)..november(2022)) shouldBe null
        }

        TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy()))!!.let {
            it.periode shouldBe år(2021)
            it.krympTilPeriode(1.januar(2022)) shouldBe null
        }
    }

    @Test
    fun `ekvivalent innenfor periode med seg selv`() {
        val clock = TikkendeKlokke()
        val expectedPeriode = år(2022)
        val tidslinje = TidslinjeForUtbetalinger.fra(utbetalinger(clock, utbetalingslinjeNy(periode = expectedPeriode)))
        tidslinje!!.ekvivalentMedInnenforPeriode(tidslinje, expectedPeriode) shouldBe true
    }

    @Test
    fun `ekvivalent innenfor periode der 2 tidslinjer er ulik`() {
        val clock = TikkendeKlokke()
        val tidslinje1 = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                utbetalingslinjeNy(periode = år(2022), clock = clock),
            ),
        )!!

        val tidslinje2Linje1 = utbetalingslinjeNy(
            periode = januar(2022)..mai(2022),
            clock = clock,
        )
        val tidslinje2 = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                tidslinje2Linje1,
                utbetalingslinjeOpphørt(
                    utbetalingslinjeSomSkalEndres = tidslinje2Linje1,
                    clock = clock,
                ),
                utbetalingslinjeNy(
                    periode = juni(2022)..desember(2022),
                    rekkefølge = Rekkefølge.TREDJE,
                    forrigeUtbetalingslinjeId = tidslinje2Linje1.id,
                    clock = clock,
                ),
            ),
        )!!
        tidslinje1.ekvivalentMedInnenforPeriode(
            other = tidslinje2,
            periode = juni(2022)..desember(2022),
        ) shouldBe true
    }

    @Test
    fun `false selv om 2 ulike tidslinjer er ekvivalente, men perioden som sjekkes for har ikke noe relevans`() {
        val clock = TikkendeKlokke()
        val tidslinje1 = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                utbetalingslinjeNy(
                    periode = år(2022),
                    clock = clock,
                ),
            ),
        )!!
        val tidslinje2Linje1Id = UUID30.randomUUID()
        val tidslinje2 = TidslinjeForUtbetalinger.fra(
            utbetalinger(
                clock,
                utbetalingslinjeNy(
                    id = tidslinje2Linje1Id,
                    periode = januar(2022)..mai(2022),
                    rekkefølge = Rekkefølge.FØRSTE,
                    clock = clock,
                ),
                utbetalingslinjeNy(
                    periode = juni(2022)..desember(2022),
                    forrigeUtbetalingslinjeId = tidslinje2Linje1Id,
                    rekkefølge = Rekkefølge.ANDRE,
                    clock = clock,
                ),
            ),
        )!!
        tidslinje1.ekvivalentMedInnenforPeriode(tidslinje2, juni(2023)..desember(2023)) shouldBe false
    }
}
