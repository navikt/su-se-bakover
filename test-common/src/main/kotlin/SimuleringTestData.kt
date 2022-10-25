package no.nav.su.se.bakover.test

import arrow.core.Either
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

private data class UtbetalingRepoMock(
    private val eksisterendeUtbetalinger: List<Utbetaling>,
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling? {
        TODO("Not yet implemented")
    }

    override fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling? {
        TODO("Not yet implemented")
    }

    override fun hentUtbetalinger(sakId: UUID): List<Utbetaling> {
        return eksisterendeUtbetalinger
    }

    override fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering) {
        TODO("Not yet implemented")
    }

    override fun opprettUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering,
        transactionContext: TransactionContext,
    ) {
        TODO("Not yet implemented")
    }

    override fun hentUkvitterteUtbetalinger(): List<Utbetaling.OversendtUtbetaling.UtenKvittering> {
        TODO("Not yet implemented")
    }

    override fun defaultTransactionContext(): TransactionContext {
        TODO("Not yet implemented")
    }
}

fun simulerNyUtbetaling(
    sak: Sak,
    utbetaling: Utbetaling.UtbetalingForSimulering,
    beregningsperiode: Periode = Periode.create(utbetaling.tidligsteDato(), utbetaling.senesteDato()),
): Either<SimuleringFeilet, Simulering> {
    return SimuleringStub(
        clock = nåtidForSimuleringStub,
        utbetalingRepo = UtbetalingRepoMock(sak.utbetalinger),
    ).simulerUtbetaling(
        SimulerUtbetalingForPeriode(
            utbetaling = utbetaling,
            simuleringsperiode = beregningsperiode,
        ),
    )
}

fun simulerUtbetaling(
    sak: Sak,
    utbetaling: Utbetaling.UtbetalingForSimulering,
    simuleringsperiode: Periode = Periode.create(utbetaling.tidligsteDato(), utbetaling.senesteDato()),
): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
    return SimuleringStub(
        clock = nåtidForSimuleringStub,
        utbetalingRepo = UtbetalingRepoMock(sak.utbetalinger),
    ).simulerUtbetaling(
        SimulerUtbetalingForPeriode(
            utbetaling = utbetaling,
            simuleringsperiode = simuleringsperiode,
        ),
    ).map {
        utbetaling.toSimulertUtbetaling(it)
    }
}

/**
 * @param strict hvis satt til true vil kryssjekk for tidslinjer og simuleringer gjennomføres.
 */
fun simulerUtbetaling(
    sak: Sak,
    søknadsbehandling: Søknadsbehandling,
    simuleringsperiode: Periode = søknadsbehandling.periode,
    clock: Clock = tikkendeFixedClock,
    strict: Boolean = true,
): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
    return sak.lagNyUtbetaling(
        saksbehandler = saksbehandler,
        beregning = søknadsbehandling.beregning ?: throw IllegalArgumentException("Kan ikke simulere, søknadsbehandling har ingen beregning"),
        clock = clock,
        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        uføregrunnlag = søknadsbehandling.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
    ).let { utbetaling ->
        if (strict) {
            sak.simulerUtbetaling(
                utbetalingForSimulering = utbetaling,
                periode = simuleringsperiode,
                simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                    SimuleringStub(
                        clock = nåtidForSimuleringStub,
                        utbetalingRepo = UtbetalingRepoMock(sak.utbetalinger),
                    ).simulerUtbetaling(
                        SimulerUtbetalingForPeriode(
                            utbetaling = utbetalingForSimulering,
                            simuleringsperiode = periode,
                        ),
                    ).map {
                        utbetalingForSimulering.toSimulertUtbetaling(it)
                    }
                },
                kontrollerMotTidligereSimulering = søknadsbehandling.simulering,
                clock = clock,
            )
        } else {
            simulerUtbetaling(
                sak = sak,
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            )
        }
    }
}

/**
 * @param strict hvis satt til true vil kryssjekk for tidslinjer og simuleringer gjennomføres.
 */
fun simulerUtbetaling(
    sak: Sak,
    revurdering: Revurdering,
    simuleringsperiode: Periode = revurdering.periode,
    clock: Clock = tikkendeFixedClock,
    strict: Boolean = true,
): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
    return sak.lagNyUtbetaling(
        saksbehandler = saksbehandler,
        beregning = revurdering.beregning ?: throw IllegalArgumentException("Kan ikke simulere, revurdering har ingen beregning"),
        clock = clock,
        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        uføregrunnlag = revurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
    ).let { utbetaling ->
        if (strict) {
            sak.simulerUtbetaling(
                utbetalingForSimulering = utbetaling,
                periode = simuleringsperiode,
                simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                    SimuleringStub(
                        clock = nåtidForSimuleringStub,
                        utbetalingRepo = UtbetalingRepoMock(sak.utbetalinger),
                    ).simulerUtbetaling(
                        SimulerUtbetalingForPeriode(
                            utbetaling = utbetalingForSimulering,
                            simuleringsperiode = periode,
                        ),
                    ).map {
                        utbetalingForSimulering.toSimulertUtbetaling(it)
                    }
                },
                kontrollerMotTidligereSimulering = revurdering.simulering,
                clock = clock,
            )
        } else {
            simulerUtbetaling(
                sak = sak,
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            )
        }
    }
}

/**
 * @param strict hvis satt til true vil kryssjekk for tidslinjer og simuleringer gjennomføres.
 */
fun simulerUtbetaling(
    sak: Sak,
    regulering: Regulering,
    simuleringsperiode: Periode = regulering.periode,
    clock: Clock = tikkendeFixedClock,
    strict: Boolean = true,
): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
    return sak.lagNyUtbetaling(
        saksbehandler = saksbehandler,
        beregning = regulering.beregning ?: throw IllegalArgumentException("Kan ikke simulere, regulering har ingen beregning"),
        clock = clock,
        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        uføregrunnlag = regulering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
    ).let { utbetaling ->
        if (strict) {
            sak.simulerUtbetaling(
                utbetalingForSimulering = utbetaling,
                periode = simuleringsperiode,
                simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode ->
                    SimuleringStub(
                        clock = nåtidForSimuleringStub,
                        utbetalingRepo = UtbetalingRepoMock(sak.utbetalinger),
                    ).simulerUtbetaling(
                        SimulerUtbetalingForPeriode(
                            utbetaling = utbetalingForSimulering,
                            simuleringsperiode = periode,
                        ),
                    ).map {
                        utbetalingForSimulering.toSimulertUtbetaling(it)
                    }
                },
                kontrollerMotTidligereSimulering = regulering.simulering,
                clock = clock,
            )
        } else {
            simulerUtbetaling(
                sak = sak,
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            )
        }
    }
}

/**
 * Ved simulering av nye utbetalingslinjer (søknadsbehandling eller revurdering som fører til endring).
 * Ved opphør bruk simuleringOpphørt()
 */
fun simuleringNy(
    beregning: Beregning = beregning(periode = år(2021)),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
    // TODO default her bør fjernes og sendes inn fra behandlingen
    uføregrunnlag: List<Grunnlag.Uføregrunnlag> = listOf(
        Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            periode = beregning.periode,
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 0,
        ),
    ),
): Simulering {
    return Utbetalingsstrategi.NyUføreUtbetaling(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        beregning = beregning,
        clock = clock,
        uføregrunnlag = uføregrunnlag,
        kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).generate().let {
        SimuleringStub(
            clock = nåtidForSimuleringStub, // Overstyr klokke slik at vi kan simulere feilutbetalinger tilbake i tid,
            utbetalingRepo = UtbetalingRepoMock(eksisterendeUtbetalinger),
        ).simulerUtbetaling(
            SimulerUtbetalingForPeriode(
                utbetaling = it,
                simuleringsperiode = beregning.periode,
            ),
        )
    }.getOrFail()
}

fun simuleringStans(
    stansDato: LocalDate,
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
): Simulering {
    return stansUtbetalingForSimulering(
        stansDato = stansDato,
        fnr = fnr,
        sakId = sakId,
        saksnummer = saksnummer,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).let {
        val stans = it.utbetalingslinjer
            .filterIsInstance<Utbetalingslinje.Endring.Stans>()
            .single()

        SimuleringStub(
            clock = clock,
            utbetalingRepo = UtbetalingRepoMock(eksisterendeUtbetalinger),
        ).simulerUtbetaling(
            SimulerUtbetalingForPeriode(
                utbetaling = it,
                simuleringsperiode = stans.periode,
            ),
        )
    }.getOrFail()
}

fun simuleringGjenopptak(
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
): Simulering {
    return Utbetalingsstrategi.Gjenoppta(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = clock,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).generer().getOrFail().let {
        val reaktivering = it.utbetalingslinjer
            .filterIsInstance<Utbetalingslinje.Endring.Reaktivering>()
            .single()

        SimuleringStub(
            clock = clock,
            utbetalingRepo = UtbetalingRepoMock(eksisterendeUtbetalinger),
        ).simulerUtbetaling(
            SimulerUtbetalingForPeriode(
                utbetaling = it,
                simuleringsperiode = reaktivering.periode,
            ),
        )
    }.getOrFail()
}

fun simuleringOpphørt(
    opphørsperiode: Periode,
    eksisterendeUtbetalinger: List<Utbetaling>,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    clock: Clock = fixedClock,
): Simulering {
    return Utbetalingsstrategi.Opphør(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        behandler = saksbehandler,
        clock = clock,
        periode = opphørsperiode,
        sakstype = Sakstype.UFØRE, // TODO("simulering_utbetaling_alder utled fra sak/behandling")
    ).generate().let {
        val opphør = it.utbetalingslinjer
            .filterIsInstance<Utbetalingslinje.Endring.Opphør>()
            .single()

        SimuleringStub(
            clock = nåtidForSimuleringStub, // Overstyr klokke slik at vi kan simulere feilutbetalinger tilbake i tid,
            utbetalingRepo = UtbetalingRepoMock(eksisterendeUtbetalinger),
        ).simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = it,
                simuleringsperiode = opphør.periode,
            ),
        )
    }.getOrFail()
}

fun simulering(
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    perioder: List<Periode> = år(2021).måneder(),
    simulertePerioder: List<SimulertPeriode> = perioder.map { simulertPeriode(it) },
): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = fixedLocalDate,
        nettoBeløp = simulertePerioder.sumOf { it.bruttoYtelse() },
        periodeList = simulertePerioder,
    )
}

fun simuleringFeilutbetaling(
    vararg perioder: Periode,
    simulertePerioder: List<SimulertPeriode> = perioder.map { it.måneder() }.flatten()
        .map { simulertPeriodeFeilutbetaling(it) },
): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = fixedLocalDate,
        nettoBeløp = simulertePerioder.sumOf { it.bruttoYtelse() },
        periodeList = simulertePerioder,
    )
}

fun simulertPeriode(
    periode: Periode,
    simulerteUtbetalinger: List<SimulertUtbetaling> = listOf(simulertUtbetaling(periode)),
): SimulertPeriode = SimulertPeriode(
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    utbetaling = simulerteUtbetalinger,
)

fun simulertPeriodeFeilutbetaling(
    periode: Periode,
    simulerteUtbetalinger: List<SimulertUtbetaling> = listOf(
        simulertUtbetaling(
            periode = periode,
            simulertDetaljer = listOf(
                simulertDetaljFeilutbetaling(periode, 15000),
                simulertDetaljTilbakeføring(periode, 15000),
                simulertDetaljOrdinær(periode, 7000),
            ),
        ),
    ),
): SimulertPeriode = SimulertPeriode(
    fraOgMed = periode.fraOgMed,
    tilOgMed = periode.tilOgMed,
    utbetaling = simulerteUtbetalinger,
)

fun simulertUtbetaling(
    periode: Periode,
    simulertDetaljer: List<SimulertDetaljer> = listOf(simulertDetaljOrdinær(periode, 15000)),
): SimulertUtbetaling = SimulertUtbetaling(
    fagSystemId = "",
    utbetalesTilId = fnr,
    utbetalesTilNavn = "",
    forfall = periode.fraOgMed.plusDays(5),
    feilkonto = false,
    detaljer = simulertDetaljer,
)

fun simulertDetaljOrdinær(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = beløp,
    tilbakeforing = false,
    sats = beløp,
    typeSats = "MND",
    antallSats = 1,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "",
    klasseType = KlasseType.YTEL,
)

fun simulertDetaljFeilutbetaling(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = beløp,
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.KL_KODE_FEIL_INNT,
    klassekodeBeskrivelse = "Feilutbetaling Inntektsytelser",
    klasseType = KlasseType.FEIL,
)

fun simulertDetaljTidligereUtbetalt(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = beløp,
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "",
    klasseType = KlasseType.YTEL,
)

fun simulertDetaljTilbakeføring(
    periode: Periode,
    beløp: Int,
): SimulertDetaljer = SimulertDetaljer(
    faktiskFraOgMed = periode.fraOgMed,
    faktiskTilOgMed = periode.tilOgMed,
    konto = "",
    belop = -beløp,
    tilbakeforing = true,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "",
    klasseType = KlasseType.YTEL,
)
