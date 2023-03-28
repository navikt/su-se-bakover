package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
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
 *  - Stønadsperioden kan ikke være lenger enn til og med måneden søker blir 67 år.
 *      - Begrensninger:
 *          - Ikke tatt høyde for SU-Alder
 *          - Det er ikke sikkert at personen har fødselsinformasjon (ikke garantert fra api)
 */
fun Sak.oppdaterStønadsperiodeForSøknadsbehandling(
    søknadsbehandlingId: UUID,
    stønadsperiode: Stønadsperiode,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
    saksbehandler: NavIdentBruker.Saksbehandler,
    hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse?,
): Either<Sak.KunneIkkeOppdatereStønadsperiode, Pair<Sak, VilkårsvurdertSøknadsbehandling>> {
    val søknadsbehandling = søknadsbehandlinger.singleOrNull {
        it.id == søknadsbehandlingId
    } ?: return Sak.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()

    validerOverlappendeStønadsperioder(stønadsperiode.periode, clock).onLeft {
        return Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(it).left()
    }

    val person = hentPerson(this.fnr).getOrElse {
        throw IllegalStateException("Kunne ikke hente person. Denne var hentet for ikke så lenge siden")
    }

    val vurdering = Aldersvurdering.Vurdert.vurder(
        stønadsperiode = stønadsperiode,
        person = person,
        saksbehandlersAvgjørelse = saksbehandlersAvgjørelse,
        clock = clock,
    ).getOrElse {
        return Sak.KunneIkkeOppdatereStønadsperiode.AldersvurderingGirIkkeRettPåUføre(it).left()
    }
    return internalOppdater(
        søknadsbehandling = søknadsbehandling,
        formuegrenserFactory = formuegrenserFactory,
        saksbehandler = saksbehandler,
        vurdering = vurdering,
        clock = clock,
    )
}

private fun Sak.internalOppdater(
    søknadsbehandling: Søknadsbehandling,
    formuegrenserFactory: FormuegrenserFactory,
    saksbehandler: NavIdentBruker.Saksbehandler,
    vurdering: Aldersvurdering.Vurdert,
    clock: Clock,
): Either<Sak.KunneIkkeOppdatereStønadsperiode, Pair<Sak, VilkårsvurdertSøknadsbehandling>> {
    søknadsbehandling.oppdaterStønadsperiodeForSaksbehandler(
        aldersvurdering = vurdering,
        formuegrenserFactory = formuegrenserFactory,
        clock = clock,
        saksbehandler = saksbehandler,
        avkorting = this.hentUteståendeAvkortingForSøknadsbehandling().fold({ it }, { it }).kanIkke(),
    ).getOrElse {
        return when (it) {
            is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata -> {
                Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
            }

            is no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeOppdatereStønadsperiode.UgyldigTilstand -> {
                Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
            }
        }.left()
    }.let { søknadsbehandlingMedOppdatertStønadsperiode ->
        return Pair(
            this.copy(
                søknadsbehandlinger = søknadsbehandlinger.filterNot { it.id == søknadsbehandlingMedOppdatertStønadsperiode.id } + søknadsbehandlingMedOppdatertStønadsperiode,
            ),
            søknadsbehandlingMedOppdatertStønadsperiode,
        ).right()
    }
}
