package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.iverksettAvslagSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg.iverksettInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock

/**
 * Iverksetter søknadsbehandlingen uten sideeffekter.
 * IO: Dersom iverksettingen fører til avslag og det er bestilt brev, genereres brevet.
 * IO: Dersom iverksettingen fører til innvilgelse simuleres utbetalingen.
 *
 * Begrensninger:
 * - Saken må inneholde søknadsbehandlings IDen som angis.
 * - Tilstanden til søknadsbehandlingen må være [Søknadsbehandling.TilAttestering]
 * - Dersom dette er en manuell iverksetting (unntak: manglende dokumentasjon), kan ikke saksbehandler og attestant være den samme.
 * - Ved innvilgelse: Det kan ikke finnes åpne kravgrunnlag på saken.
 * - Ved innvilgelse: Dersom det finnes uhåndterte avkortingsvarsler på saken, må disse håndteres av denne behandlingen i sin helhet.
 * - Stønadsperioden kan ikke overlappe tidligere stønadsperioder, med noen unntak:
 *     - Opphørte måneder som ikke har ført til utbetaling kan "overskrives".
 *     - Opphørte måneder med feilutbetaling som har blitt 100% tilbakekrevet kan "overskrives".
 */
fun Sak.iverksettSøknadsbehandling(
    command: IverksettSøknadsbehandlingCommand,
    lagDokument: (visitable: Visitable<LagBrevRequestVisitor>) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    clock: Clock,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattSøknadsbehandlingResponse<out Søknadsbehandling.Iverksatt>> {
    val søknadsbehandling = hentSøknadsbehandlingEllerKast(command)

    validerTotrinnskontroll(command, søknadsbehandling).tapLeft {
        return it.left()
    }
    return when (søknadsbehandling) {
        is Søknadsbehandling.TilAttestering.Innvilget -> iverksettInnvilgetSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            attestering = command.attestering,
            clock = clock,
            simulerUtbetaling = simulerUtbetaling,
        )

        is Søknadsbehandling.TilAttestering.Avslag -> iverksettAvslagSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            attestering = command.attestering,
            clock = clock,
            lagDokument = lagDokument,
        )
    }
}

private fun Sak.hentSøknadsbehandlingEllerKast(command: IverksettSøknadsbehandlingCommand): Søknadsbehandling.TilAttestering {
    return hentSøknadsbehandling(command.behandlingId).getOrHandle {
        throw IllegalArgumentException("Fant ikke behandling ${command.behandlingId} for sak $id")
    }.let {
        (it as? Søknadsbehandling.TilAttestering)
            ?: throw IllegalArgumentException("Prøvde iverksette søknadsbehandling som ikke var til attestering. Sak $id, søknadsbehandling ${it.id} og status ${it::class.simpleName}")
    }
}

private fun validerTotrinnskontroll(
    command: IverksettSøknadsbehandlingCommand,
    søknadsbehandling: Søknadsbehandling.TilAttestering,
): Either<KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, Unit> {
    return if (command.saksbehandlerOgAttestantKanIkkeVæreDenSamme && saksbehandlerOgAttestantErLike(
            søknadsbehandling = søknadsbehandling,
            attestering = command.attestering,
        )
    ) {
        KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
    } else {
        Unit.right()
    }
}

private fun saksbehandlerOgAttestantErLike(
    søknadsbehandling: Søknadsbehandling.TilAttestering,
    attestering: Attestering,
): Boolean = søknadsbehandling.saksbehandler.navIdent == attestering.attestant.navIdent
