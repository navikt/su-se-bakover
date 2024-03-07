package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.KanOppdaterePeriodeBosituasjonVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

/**
 * Begrensninger:
 * - Stønadsperioden må være etter alle eksisterende, ikke-opphørte stønadsperioder.
 * - Stønadsperioden kan ikke overlappe med tidligere utbetalte måneder, med noen unntak:
 *     - Stønadsperioden kan overlappe med opphørte måneder dersom de aldri har vært utbetalt.
 *     - Stønadsperioden kan overlappe med opphørte måneder som har blitt tilbakekrevet.
 *     - Stønadsperioden kan overlappe med opphørte måneder som ikke har blitt tilbakekrevet. Her må saksbehandler selv lage en økonomioppgave så det ikke blir dobbeltutbetaling.
 *  - Stønadsperioden kan ikke være lenger enn til og med måneden søker blir 67 år.
 *      - Begrensninger:
 *          - Ikke tatt høyde for SU-Alder
 *          - Det er ikke sikkert at personen har fødselsinformasjon (ikke garantert fra api)
 */
fun Sak.oppdaterStønadsperiodeForSøknadsbehandling(
    søknadsbehandlingId: SøknadsbehandlingId,
    stønadsperiode: Stønadsperiode,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
    saksbehandler: NavIdentBruker.Saksbehandler,
    hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse?,
): Either<Sak.KunneIkkeOppdatereStønadsperiode, Pair<Sak, VilkårsvurdertSøknadsbehandling>> {
    val søknadsbehandling = (
        søknadsbehandlinger.singleOrNull {
            it.id == søknadsbehandlingId
        } ?: return Sak.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()
        ) as KanOppdaterePeriodeBosituasjonVilkår

    validerOverlappendeStønadsperioder(stønadsperiode.periode).onLeft {
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
    søknadsbehandling: KanOppdaterePeriodeBosituasjonVilkår,
    formuegrenserFactory: FormuegrenserFactory,
    saksbehandler: NavIdentBruker.Saksbehandler,
    vurdering: Aldersvurdering.Vurdert,
    clock: Clock,
): Either<Sak.KunneIkkeOppdatereStønadsperiode, Pair<Sak, VilkårsvurdertSøknadsbehandling>> {
    søknadsbehandling.oppdaterStønadsperiode(
        aldersvurdering = vurdering,
        formuegrenserFactory = formuegrenserFactory,
        clock = clock,
        saksbehandler = saksbehandler,
    ).getOrElse {
        return when (it) {
            is KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata -> {
                Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
            }

            is KunneIkkeOppdatereStønadsperiode.UgyldigTilstand -> {
                Sak.KunneIkkeOppdatereStønadsperiode.KunneIkkeOppdatereGrunnlagsdata(it)
            }
        }.left()
    }.let { søknadsbehandlingMedOppdatertStønadsperiode ->
        return Pair(
            this.oppdaterSøknadsbehandling(søknadsbehandlingMedOppdatertStønadsperiode),
            søknadsbehandlingMedOppdatertStønadsperiode,
        ).right()
    }
}
