package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.finnesOverlappendeÅpenBehandling
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.validerOverlappendeStønadsperioder
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
    hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
): Either<Sak.KunneIkkeOppdatereStønadsperiode, Triple<Sak, Søknadsbehandling.Vilkårsvurdert, VerifiseringsMelding>> {
    val søknadsbehandling = søknadsbehandlinger.singleOrNull {
        it.id == søknadsbehandlingId
    } ?: return Sak.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()

    if (finnesOverlappendeÅpenBehandling(stønadsperiode.periode, søknadsbehandlingId)) {
        return Sak.KunneIkkeOppdatereStønadsperiode.FinnesOverlappendeÅpenBehandling.left()
    }

    validerOverlappendeStønadsperioder(stønadsperiode.periode, clock).onLeft {
        return Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(it).left()
    }

    val verifisertStønadsperiodeOppMotPersonsAlder = hentPerson(this.fnr).let {
        VerifisertStønadsperiodeOppMotPersonsAlder.verifiser(
            stønadsperiode = stønadsperiode,
            person = it.getOrNull(),
            clock = clock,
        )
    }.getOrElse {
        return Sak.KunneIkkeOppdatereStønadsperiode.ValideringsfeilAvStønadsperiodeOgPersonsAlder(it).left()
    }

    return søknadsbehandling.oppdaterStønadsperiodeForSaksbehandler(
        oppdatertStønadsperiode = verifisertStønadsperiodeOppMotPersonsAlder,
        formuegrenserFactory = formuegrenserFactory,
        clock = clock,
        saksbehandler = saksbehandler,
        avkorting = this.hentUteståendeAvkortingForSøknadsbehandling().fold({ it }, { it }).kanIkke(),
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
        Triple(
            this.copy(
                søknadsbehandlinger = søknadsbehandlinger.filterNot { it.id == søknadsbehandlingMedOppdatertStønadsperiode.id } + søknadsbehandlingMedOppdatertStønadsperiode,
            ),
            søknadsbehandlingMedOppdatertStønadsperiode,
            verifisertStønadsperiodeOppMotPersonsAlder.verifiseringsMelding,
        )
    }
}
