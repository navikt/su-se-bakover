package no.nav.su.se.bakover.web.routes.sak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.UtbetalingJson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.uføre.domain.Uføregrad
import økonomi.domain.utbetaling.Utbetalingslinje
import java.time.Clock
import java.util.UUID
import kotlin.random.Random

internal class SakJsonTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Random.nextLong(2021, Long.MAX_VALUE)
    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(saksnummer),
        opprettet = fixedTidspunkt,
        fnr = Fnr("12345678910"),
        utbetalinger = Utbetalinger(),
        type = Sakstype.UFØRE,
        versjon = Hendelsesversjon(1),
        uteståendeKravgrunnlag = null,
    )

    //language=JSON
    private val sakJsonString =
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
                "vedtak": [],
                "klager": [],
                "reguleringer": [],
                "sakstype": "uføre",
                "vedtakPåTidslinje": [],
                "utenlandsopphold": {"utenlandsopphold": [], "antallDager":  0},
                "versjon": 1,
                "tilbakekrevinger": [],
                "uteståendeKravgrunnlag": null
            }
        """.trimIndent()

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(sakJsonString, serialize(sak.toJson(fixedClock, formuegrenserFactoryTestPåDato())), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SakJson>(sakJsonString) shouldBe sak.toJson(fixedClock, formuegrenserFactoryTestPåDato())
    }

    @Nested
    inner class UtbetalingslinjerTest {

        @Test
        fun `mapper opphørte utbetalingslinjer riktig`() {
            val nyUtbetaling = Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                rekkefølge = Rekkefølge.start(),
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 17900,
                uføregrad = Uføregrad.parse(50),
            )
            val midlertidigStans = Utbetalingslinje.Endring.Stans(
                utbetalingslinjeSomSkalEndres = nyUtbetaling,
                virkningstidspunkt = 1.februar(2021),
                clock = Clock.systemUTC(),
                rekkefølge = Rekkefølge.start(),
            )
            val reaktivering = Utbetalingslinje.Endring.Reaktivering(
                utbetalingslinjeSomSkalEndres = midlertidigStans,
                virkningstidspunkt = 1.mars(2021),
                clock = Clock.systemUTC(),
                rekkefølge = Rekkefølge.start(),
            )
            val opphørslinje = Utbetalingslinje.Endring.Opphør(
                utbetalingslinjeSomSkalEndres = reaktivering,
                virkningsperiode = Periode.create(1.april(2021), reaktivering.periode.tilOgMed),
                clock = Clock.systemUTC(),
                rekkefølge = Rekkefølge.start(),
            )

            val utbetaling1 = oversendtUtbetalingUtenKvittering(
                utbetalingslinjer = nonEmptyListOf(nyUtbetaling),
                opprettet = nyUtbetaling.opprettet,
            )
            val utbetaling2 = oversendtUtbetalingUtenKvittering(
                utbetalingslinjer = nonEmptyListOf(midlertidigStans),
                opprettet = midlertidigStans.opprettet,

            )
            val utbetaling3 = oversendtUtbetalingUtenKvittering(
                utbetalingslinjer = nonEmptyListOf(reaktivering),
                opprettet = reaktivering.opprettet,
            )
            val utbetaling4 = oversendtUtbetalingUtenKvittering(
                utbetalingslinjer = nonEmptyListOf(opphørslinje),
                opprettet = opphørslinje.opprettet,
            )

            val sak = sak.copy(utbetalinger = Utbetalinger(utbetaling1, utbetaling2, utbetaling3, utbetaling4))

            val (actual1, actual2, actual3, actual4) = sak.toJson(fixedClock, formuegrenserFactoryTestPåDato()).utbetalinger
            actual1 shouldBe UtbetalingJson(
                fraOgMed = nyUtbetaling.periode.fraOgMed,
                tilOgMed = midlertidigStans.periode.fraOgMed.minusDays(1),
                beløp = nyUtbetaling.beløp,
                type = "NY",
            )
            actual2 shouldBe UtbetalingJson(
                fraOgMed = midlertidigStans.periode.fraOgMed,
                tilOgMed = reaktivering.periode.fraOgMed.minusDays(1),
                beløp = 0,
                type = "STANS",
            )
            actual3 shouldBe UtbetalingJson(
                fraOgMed = reaktivering.periode.fraOgMed,
                tilOgMed = opphørslinje.periode.fraOgMed.minusDays(1),
                beløp = nyUtbetaling.beløp,
                type = "GJENOPPTA",
            )
            actual4 shouldBe UtbetalingJson(
                fraOgMed = opphørslinje.periode.fraOgMed,
                tilOgMed = nyUtbetaling.periode.tilOgMed,
                beløp = 0,
                type = "OPPHØR",
            )
        }
    }
}
