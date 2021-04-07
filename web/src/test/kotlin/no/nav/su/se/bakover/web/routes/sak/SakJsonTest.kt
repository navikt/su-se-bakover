package no.nav.su.se.bakover.web.routes.sak

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class SakJsonTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Math.random().toLong()
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
            val utbetalingsid = UUID30.randomUUID()
            val førsteUtbetaling = Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.now(),
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 17900,
            )
            val opphørslinje = Utbetalingslinje.Endring(
                id = utbetalingsid,
                opprettet = Tidspunkt.now(),
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 17900,
                statusendring = Utbetalingslinje.Statusendring(
                    status = Utbetalingslinje.LinjeStatus.OPPHØR,
                    fraOgMed = 1.juni(2021),
                ),
            )

            val utbetaling1 = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
                on { utbetalingslinjer } doReturn listOf(førsteUtbetaling)
                on { type } doReturn Utbetaling.UtbetalingsType.NY
                on { opprettet } doReturn førsteUtbetaling.opprettet
            }
            val utbetaling2 = mock<Utbetaling.OversendtUtbetaling.UtenKvittering> {
                on { utbetalingslinjer } doReturn listOf(opphørslinje)
                on { type } doReturn Utbetaling.UtbetalingsType.OPPHØR
                on { opprettet } doReturn opphørslinje.opprettet
            }

            val sak = sak.copy(utbetalinger = listOf(utbetaling1, utbetaling2))

            val (actual1, actual2) = sak.toJson().utbetalinger
            actual1 shouldBe UtbetalingJson(
                id = førsteUtbetaling.id.toString(),
                fraOgMed = førsteUtbetaling.fraOgMed,
                tilOgMed = opphørslinje.statusendring.fraOgMed.minusDays(1),
                beløp = førsteUtbetaling.beløp,
                type = utbetaling1.type.toString(),
            )
            actual2 shouldBe UtbetalingJson(
                id = opphørslinje.id.toString(),
                fraOgMed = opphørslinje.statusendring.fraOgMed,
                tilOgMed = opphørslinje.tilOgMed,
                beløp = 0,
                type = utbetaling2.type.toString(),
            )
        }
    }
}
