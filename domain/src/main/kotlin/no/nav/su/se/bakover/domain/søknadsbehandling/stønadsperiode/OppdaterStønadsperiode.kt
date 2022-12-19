package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.time.Clock
import java.util.UUID

/**
 * Begrensninger:
 * - Stønadsperioden må være etter alle eksisterende, ikke-opphørte stønadsperioder.
 * - Stønadsperioden kan ikke overlappe med tidligere utbetalte måneder, med noen unntak:
 *     - Stønadsperioden kan overlappe med opphørte måneder dersom de aldri har vært utbetalt.
 *     - Stønadsperioden kan overlappe med opphørte måneder som har blitt tilbakekrevet.
 *     - Stønadsperioden kan ikke overlappe med opphørte måneder som ikke har blitt tilbakekrevet. Dette på grunn av en bug/feature i økonomisystemet, der disse tilfellene vil føre til dobbelt-utbetalinger.
 *     - Stønadsperioden kan ikke overlappe med opphørte måneder som har ført til avkortingsvarsel (via en revurdering).
 */
fun Sak.oppdaterStønadsperiodeForSøknadsbehandling(
    søknadsbehandlingId: UUID,
    stønadsperiode: Stønadsperiode,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
    saksbehandler: NavIdentBruker.Saksbehandler,
): Either<Sak.KunneIkkeOppdatereStønadsperiode, Pair<Sak, Søknadsbehandling.Vilkårsvurdert>> {
    val søknadsbehandling = søknadsbehandlinger.singleOrNull {
        it.id == søknadsbehandlingId
    } ?: return Sak.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()

    hentIkkeOpphørtePerioder().let { stønadsperioder ->
        if (stønadsperioder.any { it overlapper stønadsperiode.periode }) {
            return Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeOverlapperMedLøpendeStønadsperiode.left()
        }
        if (stønadsperioder.any { it.starterSamtidigEllerSenere(stønadsperiode.periode) }) {
            return Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeForSenerePeriodeEksisterer.left()
        }
    }

    hentGjeldendeVedtaksdata(
        periode = stønadsperiode.periode,
        clock = clock,
    ).map {
        if (it.inneholderOpphørsvedtakMedAvkortingUtenlandsopphold()) {
            // TODO jah: Trenger vi gjøre den neste sjekken her? Vi har identifisert at stønadsperioden har tidligere revurderte, opphørte måneder som laget avkortingsvarsel.
            val alleUtbetalingerErOpphørt =
                utbetalingstidslinje(periode = stønadsperiode.periode).tidslinje.all { utbetalingslinjePåTidslinje ->
                    utbetalingslinjePåTidslinje is UtbetalingslinjePåTidslinje.Opphør
                }

            if (!alleUtbetalingerErOpphørt) {
                // Man kan ikke ha stønadsperioder over måneder med opphør som førte til eller ville ha ført til feilkonto, les: feilutbetalinger og avkortinger. Dersom man ønsker å endre disse månedene, må de revurderes.
                return Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold.left()
            }
        }
    }

    return søknadsbehandling.oppdaterStønadsperiode(
        oppdatertStønadsperiode = stønadsperiode,
        formuegrenserFactory = formuegrenserFactory,
        clock = clock,
        saksbehandler = saksbehandler,
    ).mapLeft {
        when (it) {
            is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata -> {
                Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
            }

            is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode.UgyldigTilstand -> {
                Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
            }
        }
    }.map { søknadsbehandlingMedOppdatertStønadsperiode ->
        Pair(
            this.copy(
                søknadsbehandlinger = søknadsbehandlinger.filterNot { it.id == søknadsbehandlingMedOppdatertStønadsperiode.id } + søknadsbehandlingMedOppdatertStønadsperiode,
            ),
            søknadsbehandlingMedOppdatertStønadsperiode,
        )
    }
}
