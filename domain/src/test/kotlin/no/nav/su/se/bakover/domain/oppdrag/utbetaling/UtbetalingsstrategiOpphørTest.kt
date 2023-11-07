package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class UtbetalingsstrategiOpphørTest {
    @Test
    fun `kaster exception dersom det ikke eksisterer utbetalinger som kan opphøres`() {
        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Opphør(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                eksisterendeUtbetalinger = Utbetalinger(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                periode = januar(2021),
                sakstype = Sakstype.UFØRE,
            ).generate()
        }.also {
            it.message shouldBe "Ingen oversendte utbetalinger å opphøre"
        }
    }

    @Test
    fun `kaster exception dersom man forsøker å opphøre utbetalinger senere enn siste utbetalingslinje sin sluttdato`() {
        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Opphør(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                eksisterendeUtbetalinger = Utbetalinger(kvittertUtbetaling()),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                periode = januar(2022),
                sakstype = Sakstype.UFØRE,
            ).generate()
        }.also {
            it.message shouldBe "Dato for opphør må være tidligere enn tilOgMed for siste utbetalingslinje"
        }
    }

    @Test
    fun `lager opphør for siste utbetalingslinje`() {
        val siste = kvittertUtbetaling()
        Utbetalingsstrategi.Opphør(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(siste),
            behandler = saksbehandler,
            clock = fixedClock,
            periode = Periode.create(
                1.mars(2021),
                siste.sisteUtbetalingslinje().periode.tilOgMed,
            ),
            sakstype = Sakstype.UFØRE,
        ).generate().also {
            it.sakId shouldBe sakId
            it.saksnummer shouldBe saksnummer
            it.fnr shouldBe fnr
            it.behandler shouldBe saksbehandler
            it.utbetalingslinjer shouldBe nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    id = it.utbetalingslinjer.single().id,
                    opprettet = it.utbetalingslinjer.single().opprettet,
                    rekkefølge = Rekkefølge.start(),
                    fraOgMed = siste.utbetalingslinjer.single().periode.fraOgMed,
                    tilOgMed = siste.utbetalingslinjer.single().periode.tilOgMed,
                    beløp = siste.utbetalingslinjer.single().beløp,
                    forrigeUtbetalingslinjeId = siste.utbetalingslinjer.single().forrigeUtbetalingslinjeId,
                    virkningsperiode = Periode.create(1.mars(2021), siste.utbetalingslinjer.single().periode.tilOgMed),
                    uføregrad = siste.utbetalingslinjer.single().uføregrad,
                ),
            )
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, _, _) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        Utbetalingsstrategi.Opphør(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            eksisterendeUtbetalinger = sak.utbetalinger,
            behandler = saksbehandler,
            sakstype = sak.type,
            periode = mars(2021),
            clock = tikkendeKlokke,
        ).generate().let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Opphør(
                        id = sak.utbetalinger.last().sisteUtbetalingslinje().id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        rekkefølge = Rekkefølge.start(),
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = sak.utbetalinger.last().sisteUtbetalingslinje().forrigeUtbetalingslinjeId,
                        virkningsperiode = mars(2021),
                        beløp = 21989,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        rekkefølge = Rekkefølge.skip(0),
                        fraOgMed = 1.april(2021),
                        tilOgMed = 30.april(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[2].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        rekkefølge = Rekkefølge.skip(1),
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].id,
                        beløp = 21989,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger - stans`() {
        val tikkendeKlokke = TikkendeKlokke(1.april(2021).fixedClock())
        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        val (sak2, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = mai(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakSomKanRevurderes,
            clock = tikkendeKlokke,
        )

        Utbetalingsstrategi.Opphør(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            eksisterendeUtbetalinger = sak2.utbetalinger,
            behandler = saksbehandler,
            sakstype = sak.type,
            periode = januar(2021),
            clock = tikkendeKlokke,
        ).generate().let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Opphør(
                        id = sak2.utbetalinger.last().sisteUtbetalingslinje().id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        rekkefølge = Rekkefølge.start(),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = sak2.utbetalinger.last().sisteUtbetalingslinje().forrigeUtbetalingslinjeId,
                        virkningsperiode = januar(2021),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        rekkefølge = Rekkefølge.skip(0),
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Stans(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        rekkefølge = Rekkefølge.skip(1),
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger - opphør`() {
        val tikkendeKlokke = TikkendeKlokke(1.februar(2021).fixedClock())
        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        val (sak2, _) = iverksattRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = mai(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakSomKanRevurderes,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    periode = mai(2021).rangeTo(desember(2021)),
                ),
            ),
        )

        Utbetalingsstrategi.Opphør(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            eksisterendeUtbetalinger = sak2.utbetalinger,
            behandler = saksbehandler,
            sakstype = sak.type,
            periode = januar(2021),
            clock = tikkendeKlokke,
        ).generate().let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Opphør(
                        id = sak2.utbetalinger.last().sisteUtbetalingslinje().id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        rekkefølge = Rekkefølge.start(),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = sak2.utbetalinger.last().sisteUtbetalingslinje().forrigeUtbetalingslinjeId,
                        virkningsperiode = januar(2021),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        rekkefølge = Rekkefølge.skip(0),
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Opphør(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        rekkefølge = Rekkefølge.skip(1),
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger - reaktivering`() {
        val tikkendeKlokke = TikkendeKlokke(1.april(2021).fixedClock())
        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        val (sak2, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = mai(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakSomKanRevurderes,
            clock = tikkendeKlokke,
        )

        val (sak3, _) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            periode = mai(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak2 to stans,
            clock = tikkendeKlokke,
        )

        Utbetalingsstrategi.Opphør(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            eksisterendeUtbetalinger = sak3.utbetalinger,
            behandler = saksbehandler,
            sakstype = sak.type,
            periode = januar(2021),
            clock = tikkendeKlokke,
        ).generate().let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Opphør(
                        id = sak2.utbetalinger.last().sisteUtbetalingslinje().id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        rekkefølge = Rekkefølge.start(),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = sak2.utbetalinger.last().sisteUtbetalingslinje().forrigeUtbetalingslinjeId,
                        virkningsperiode = januar(2021),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        rekkefølge = Rekkefølge.skip(0),
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Stans(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        rekkefølge = Rekkefølge.skip(1),
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Reaktivering(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[3].opprettet,
                        rekkefølge = Rekkefølge.skip(2),
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }
}
