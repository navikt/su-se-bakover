package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.test.simulering.simulerNyUtbetaling
import no.nav.su.se.bakover.test.simulering.simuleringNy
import java.time.Clock
import java.util.UUID

val avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt)
val utbetalingsRequest = Utbetalingsrequest("<xml></xml>")

fun utbetalingslinjeNy(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    clock: Clock = fixedClock,
    rekkefølge: Rekkefølge = Rekkefølge.start(),
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Ny {
    return utbetalingslinjeNy(
        id = id,
        periode = periode,
        rekkefølge = rekkefølge,
        opprettet = Tidspunkt.now(clock),
        beløp = beløp,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        uføregrad = uføregrad,
        kjøreplan = kjøreplan,
    )
}

/**
 * Syr sammen en liste med utbetalingslinjer slik at de peker på hverandre.
 * TODO jah: Bør returnere Utbetalingslinjer istedenfor List<Utbetalingslinje>
 */
fun utbetalingslinjer(
    første: Utbetalingslinje,
    andre: Utbetalingslinje,
    vararg utbetalingslinjer: Utbetalingslinje,
): List<Utbetalingslinje> {
    return listOf(første) + (listOf(første) + andre + utbetalingslinjer).zipWithNext { a, b ->
        if (b is Utbetalingslinje.Ny) {
            b.oppdaterReferanseTilForrigeUtbetalingslinje(a.id)
        } else {
            b
        }
    }
}

fun utbetalingslinjeNy(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    opprettet: Tidspunkt,
    rekkefølge: Rekkefølge = Rekkefølge.start(),
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Ny {
    return Utbetalingslinje.Ny(
        id = id,
        opprettet = opprettet,
        rekkefølge = rekkefølge,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = beløp,
        uføregrad = Uføregrad.parse(uføregrad),
        utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
    )
}

fun utbetalingslinjeOpphørt(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    rekkefølge: Rekkefølge,
    clock: Clock = fixedClock,
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Endring.Opphør {
    return Utbetalingslinje.Endring.Opphør(
        id = id,
        opprettet = Tidspunkt.now(clock),
        rekkefølge = rekkefølge,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = beløp,
        uføregrad = Uføregrad.parse(uføregrad),
        virkningsperiode = periode,
        utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
    )
}

fun utbetalingslinjeStans(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    rekkefølge: Rekkefølge,
    clock: Clock = fixedClock,
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Endring.Stans {
    return Utbetalingslinje.Endring.Stans(
        id = id,
        opprettet = Tidspunkt.now(clock),
        rekkefølge = rekkefølge,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = beløp,
        uføregrad = Uføregrad.parse(uføregrad),
        virkningsperiode = periode,
        utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
    )
}

fun utbetalingslinjeReaktivering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    rekkefølge: Rekkefølge,
    clock: Clock = fixedClock,
    beløp: Int = 15000,
    forrigeUtbetalingslinjeId: UUID30? = null,
    uføregrad: Int = 50,
    kjøreplan: UtbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
): Utbetalingslinje.Endring.Reaktivering {
    return Utbetalingslinje.Endring.Reaktivering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        rekkefølge = rekkefølge,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = beløp,
        uføregrad = Uføregrad.parse(uføregrad),
        virkningsperiode = periode,
        utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
    )
}

fun nyUtbetalingForSimulering(
    sakOgBehandling: Pair<Sak, Behandling>,
    beregning: Beregning,
    clock: Clock,
): Utbetaling.UtbetalingForSimulering {
    return sakOgBehandling.let { (sak, behandling) ->
        when (sak.type) {
            Sakstype.ALDER -> {
                Utbetalingsstrategi.NyAldersUtbetaling(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    behandler = saksbehandler,
                    sakstype = sak.type,
                    beregning = beregning,
                    clock = clock,
                    kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ).generate()
            }

            Sakstype.UFØRE -> {
                Utbetalingsstrategi.NyUføreUtbetaling(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    behandler = saksbehandler,
                    sakstype = sak.type,
                    beregning = beregning,
                    clock = clock,
                    uføregrunnlag = behandling.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                    kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ).generate()
            }
        }
    }
}

fun nyUtbetalingSimulert(
    sakOgBehandling: Pair<Sak, Behandling>,
    beregning: Beregning,
    clock: Clock,
): Utbetaling.SimulertUtbetaling {
    return sakOgBehandling.let { (sak, _) ->
        nyUtbetalingForSimulering(
            sakOgBehandling = sakOgBehandling,
            beregning = beregning,
            clock = clock,
        ).let {
            it.toSimulertUtbetaling(
                simulerNyUtbetaling(
                    sak = sak,
                    utbetaling = it,
                ).getOrFail(),
            )
        }
    }
}

fun nyUtbetalingOversendUtenKvittering(
    sakOgBehandling: Pair<Sak, Behandling>,
    beregning: Beregning,
    clock: Clock,
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return sakOgBehandling.let { (_, _) ->
        nyUtbetalingSimulert(
            sakOgBehandling = sakOgBehandling,
            beregning = beregning,
            clock = clock,
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest("<xml></xml>"),
        )
    }
}

fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    søknadsbehandling: IverksattSøknadsbehandling.Innvilget,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            clock = clock,
            periode = søknadsbehandling.periode,
            rekkefølge = Rekkefølge.start(),
        ),
    ),
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
) = oversendtUtbetalingUtenKvittering(
    id = id,
    periode = søknadsbehandling.periode,
    fnr = søknadsbehandling.fnr,
    sakId = søknadsbehandling.sakId,
    saksnummer = søknadsbehandling.saksnummer,
    clock = clock,
    utbetalingslinjer = utbetalingslinjer,
    avstemmingsnøkkel = avstemmingsnøkkel,
    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
    beregning = søknadsbehandling.beregning,
)

fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    revurdering: RevurderingTilAttestering,
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            clock = clock,
            periode = periode,
            rekkefølge = Rekkefølge.start(),
        ),
    ),
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
) = oversendtUtbetalingUtenKvittering(
    id = id,
    fnr = revurdering.fnr,
    sakId = revurdering.sakId,
    saksnummer = revurdering.saksnummer,
    clock = clock,
    utbetalingslinjer = utbetalingslinjer,
    avstemmingsnøkkel = avstemmingsnøkkel,
    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
)

fun oversendtUtbetalingUtenKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    opprettet: Tidspunkt = Tidspunkt.now(clock),
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            clock = clock,
            periode = periode,
            forrigeUtbetalingslinjeId = eksisterendeUtbetalinger.lastOrNull()?.utbetalingslinjer?.lastOrNull()?.id,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    beregning: Beregning = beregning(periode),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE,
    ).toSimulertUtbetaling(
        simulering = simuleringNy(
            beregning = beregning,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            clock = clock,
        ),
    ).toOversendtUtbetaling(
        oppdragsmelding = utbetalingsRequest,
    )
}

@Suppress("unused")
fun simulertUtbetaling(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje> = nonEmptyListOf(
        utbetalingslinjeNy(
            periode = periode,
            clock = clock,
        ),
    ),
    avstemmingsnøkkel: Avstemmingsnøkkel = no.nav.su.se.bakover.test.avstemmingsnøkkel,
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
): Utbetaling.SimulertUtbetaling {
    return Utbetaling.UtbetalingForSimulering(
        id = id,
        opprettet = Tidspunkt.now(clock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).toSimulertUtbetaling(
        simulering = simuleringNy(
            fnr = fnr,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            clock = clock,
        ),
    )
}

/**
 * Defaultverdier:
 * - id: arbitrær
 * - utbetalingsstatus: OK
 * - type: NY
 */
fun oversendtUtbetalingMedKvittering(
    id: UUID30 = UUID30.randomUUID(),
    periode: Periode = år(2021),
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    eksisterendeUtbetalinger: Utbetalinger = Utbetalinger(),
    clock: Clock = fixedClock,
    beregning: Beregning = beregning(periode),
): Utbetaling.OversendtUtbetaling.MedKvittering {
    return oversendtUtbetalingUtenKvittering(
        id = id,
        periode = periode,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        clock = clock,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        beregning = beregning,
    ).toKvittertUtbetaling(
        kvittering(
            utbetalingsstatus = utbetalingsstatus,
            clock = clock,
        ),
    )
}

/**
 * Defaultverdier:
 * - utbetalingsstatus: OK
 */
fun kvittering(
    utbetalingsstatus: Kvittering.Utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
    clock: Clock = fixedClock,
) = Kvittering(
    utbetalingsstatus = utbetalingsstatus,
    originalKvittering = "<xml></xml>",
    mottattTidspunkt = Tidspunkt.now(clock),
)
