package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.iverksettAvslagSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg.iverksettInnvilgetSøknadsbehandling
import satser.domain.SatsFactory
import java.time.Clock

/**
 * Iverksetter søknadsbehandlingen uten sideeffekter.
 * IO: Dersom iverksettingen fører til avslag og det er bestilt brev, genereres brevet.
 * IO: Dersom iverksettingen fører til innvilgelse simuleres utbetalingen.
 *
 * Begrensninger:
 * - Saken må inneholde søknadsbehandlings IDen som angis.
 * - Tilstanden til søknadsbehandlingen må være [SøknadsbehandlingTilAttestering]
 * - Dersom dette er en manuell iverksetting (unntak: manglende dokumentasjon), kan ikke saksbehandler og attestant være den samme.
 * - Ved innvilgelse: Det kan ikke finnes åpne kravgrunnlag på saken.
 * - Stønadsperioden kan ikke overlappe tidligere stønadsperioder, med noen unntak:
 *     - Opphørte måneder som ikke har ført til utbetaling kan "overskrives".
 *     - Opphørte måneder med feilutbetaling som har blitt 100% tilbakekrevet kan "overskrives".
 */
fun Sak.iverksettSøknadsbehandling(
    command: IverksettSøknadsbehandlingCommand,
    genererPdf: (command: IverksettSøknadsbehandlingDokumentCommand) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    clock: Clock,
    satsFactory: SatsFactory,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattSøknadsbehandlingResponse<out IverksattSøknadsbehandling>> {
    val søknadsbehandling = hentSøknadsbehandlingEllerKast(command)

    validerTotrinnskontroll(command, søknadsbehandling).onLeft {
        return it.left()
    }
    return when (søknadsbehandling) {
        is SøknadsbehandlingTilAttestering.Innvilget -> iverksettInnvilgetSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            attestering = command.attestering,
            clock = clock,
            simulerUtbetaling = simulerUtbetaling,
        )

        is SøknadsbehandlingTilAttestering.Avslag -> iverksettAvslagSøknadsbehandling(
            søknadsbehandling = søknadsbehandling,
            attestering = command.attestering,
            clock = clock,
            genererPdf = genererPdf,
            satsFactory = satsFactory,
        )
    }
}

private fun Sak.hentSøknadsbehandlingEllerKast(command: IverksettSøknadsbehandlingCommand): SøknadsbehandlingTilAttestering {
    return hentSøknadsbehandling(command.behandlingId).getOrElse {
        throw IllegalArgumentException("Fant ikke behandling ${command.behandlingId} for sak $id")
    }.let {
        (it as? SøknadsbehandlingTilAttestering)
            ?: throw IllegalArgumentException("Prøvde iverksette søknadsbehandling som ikke var til attestering. Sak $id, søknadsbehandling ${it.id} og status ${it::class.qualifiedName}")
    }
}

private fun validerTotrinnskontroll(
    command: IverksettSøknadsbehandlingCommand,
    søknadsbehandling: SøknadsbehandlingTilAttestering,
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
    søknadsbehandling: SøknadsbehandlingTilAttestering,
    attestering: Attestering,
): Boolean = søknadsbehandling.saksbehandler.navIdent == attestering.attestant.navIdent
