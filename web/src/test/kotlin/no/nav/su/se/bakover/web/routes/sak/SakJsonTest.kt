package no.nav.su.se.bakover.web.routes.sak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.UtbetalingJson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.util.UUID
import kotlin.random.Random

internal class SakJsonTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Random.nextLong(2021, Long.MAX_VALUE)
    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(saksnummer),
        fnr = Fnr("12345678910"),
        utbetalinger = emptyList(),
    )

    //language=JSON
    val sakJsonString =
        """
            {
                "id": "$sakId",
                "saksnummer": $saksnummer,
                "fnr": "12345678910",
                "søknader": [],
                "behandlinger" : [],
                "utbetalinger": [],
                "utbetalingerKanStansesEllerGjenopptas": "INGEN",
                "revurderinger": [],
                "vedtak": []
            }
        """.trimIndent()

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(sakJsonString, serialize(sak.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SakJson>(sakJsonString) shouldBe sak.toJson()
    }

    @Nested
    inner class UtbetalingslinjerTest {

        @Test
        fun `mapper opphørte utbetalingslinjer riktig`() {
            val nyUtbetaling = Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.now(),
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 17900,
                uføregrad = Uføregrad.parse(50),
            )
            val midlertidigStans = Utbetalingslinje.Endring.Stans(
                utbetalingslinje = nyUtbetaling,
                virkningstidspunkt = 1.februar(2021),
                clock = Clock.systemUTC(),
            )
            val reaktivering = Utbetalingslinje.Endring.Reaktivering(
                utbetalingslinje = midlertidigStans,
                virkningstidspunkt = 1.mars(2021),
                clock = Clock.systemUTC(),
            )
            val opphørslinje = Utbetalingslinje.Endring.Opphør(
                utbetalingslinje = reaktivering,
                virkningstidspunkt = 1.april(2021),
                clock = Clock.systemUTC(),
            )

            val utbetaling1 = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
                on { utbetalingslinjer } doReturn nonEmptyListOf(nyUtbetaling)
                on { type } doReturn Utbetaling.UtbetalingsType.NY
                on { opprettet } doReturn nyUtbetaling.opprettet
            }
            val utbetaling2 = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
                on { utbetalingslinjer } doReturn nonEmptyListOf(midlertidigStans)
                on { type } doReturn Utbetaling.UtbetalingsType.STANS
                on { opprettet } doReturn midlertidigStans.opprettet
            }
            val utbetaling3 = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
                on { utbetalingslinjer } doReturn nonEmptyListOf(reaktivering)
                on { type } doReturn Utbetaling.UtbetalingsType.GJENOPPTA
                on { opprettet } doReturn reaktivering.opprettet
            }
            val utbetaling4 = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
                on { utbetalingslinjer } doReturn nonEmptyListOf(opphørslinje)
                on { type } doReturn Utbetaling.UtbetalingsType.OPPHØR
                on { opprettet } doReturn opphørslinje.opprettet
            }

            val sak = sak.copy(utbetalinger = listOf(utbetaling1, utbetaling2, utbetaling3, utbetaling4))

            val (actual1, actual2, actual3, actual4) = sak.toJson().utbetalinger
            actual1 shouldBe UtbetalingJson(
                fraOgMed = nyUtbetaling.fraOgMed,
                tilOgMed = midlertidigStans.virkningstidspunkt.minusDays(1),
                beløp = nyUtbetaling.beløp,
                type = Utbetaling.UtbetalingsType.NY.toString(),
            )
            actual2 shouldBe UtbetalingJson(
                fraOgMed = midlertidigStans.virkningstidspunkt,
                tilOgMed = reaktivering.virkningstidspunkt.minusDays(1),
                beløp = 0,
                type = Utbetaling.UtbetalingsType.STANS.toString(),
            )
            actual3 shouldBe UtbetalingJson(
                fraOgMed = reaktivering.virkningstidspunkt,
                tilOgMed = opphørslinje.virkningstidspunkt.minusDays(1),
                beløp = nyUtbetaling.beløp,
                type = Utbetaling.UtbetalingsType.GJENOPPTA.toString(),
            )
            actual4 shouldBe UtbetalingJson(
                fraOgMed = opphørslinje.virkningstidspunkt,
                tilOgMed = nyUtbetaling.tilOgMed,
                beløp = 0,
                type = Utbetaling.UtbetalingsType.OPPHØR.toString(),
            )
        }
    }
}
