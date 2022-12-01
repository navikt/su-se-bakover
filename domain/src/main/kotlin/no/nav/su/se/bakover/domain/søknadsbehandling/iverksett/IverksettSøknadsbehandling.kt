package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
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

fun Sak.iverksettSøknadsbehandling(
    command: IverksettSøknadsbehandlingCommand,
    lagDokument: (visitable: Visitable<LagBrevRequestVisitor>) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    clock: Clock,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattSøknadsbehandlingResponse<out Søknadsbehandling.Iverksatt>> {
    val søknadsbehandling = hentSøknadsbehandling(command.behandlingId).getOrHandle {
        throw IllegalArgumentException("Fant ikke behandling ${command.behandlingId} for sak $id")
    }.let {
        (it as? Søknadsbehandling.TilAttestering)
            ?: throw IllegalArgumentException("Prøvde iverksette søknadsbehandling som ikke var til attestering. Sak $id, søknadsbehandling ${it.id} og status ${it::class.simpleName}")
    }

    if (
        command.saksbehandlerOgAttestantKanIkkeVæreDenSamme && saksbehandlerOgAttestantErLike(
            søknadsbehandling = søknadsbehandling,
            attestering = command.attestering,
        )
    ) {
        return KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
    }

    validerAvkorting(søknadsbehandling).tapLeft {
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

private fun Sak.validerAvkorting(søknadsbehandling: Søknadsbehandling.TilAttestering): Either<KunneIkkeIverksetteSøknadsbehandling.AvkortingErUfullstendig, Unit> {
    // Skal ikke håndtere avkorting ved avslag.
    if (søknadsbehandling !is Søknadsbehandling.TilAttestering.Innvilget) return Unit.right()

    val uteståendeAvkortingPåSak = if (uteståendeAvkorting is Avkortingsvarsel.Ingen) {
        null
    } else {
        uteståendeAvkorting as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
    }
    // TODO jah: Mulig å flytte den biten som kun angår behandlingen inn i [Søknadsbehandling.TilAttestering.Innvilget.tilIverksatt], mens det saksnære bør ligge her (som f.eks. at tilstander og IDer er like)
    return when (val a = søknadsbehandling.avkorting) {
        is AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående -> {
            val avkortingsvarselPåBehandling = a.avkortingsvarsel
            if (uteståendeAvkortingPåSak == null) {
                throw IllegalStateException("Prøver å iverksette søknadsbehandling ${søknadsbehandling.id} med utestående avkorting uten at det finnes noe å avkorte på saken for avkortingsvarsel ${avkortingsvarselPåBehandling.id}")
            }
            if (avkortingsvarselPåBehandling != uteståendeAvkortingPåSak) {
                throw IllegalStateException("Prøver å iverksette søknadsbehandling ${søknadsbehandling.id} hvor søknadsbehandlingens avkorting ${avkortingsvarselPåBehandling.id} er forskjellig fra sakens avkorting ${uteståendeAvkortingPåSak.id}")
            }
            if (!avkortingsvarselPåBehandling.fullstendigAvkortetAv(søknadsbehandling.beregning)) {
                KunneIkkeIverksetteSøknadsbehandling.AvkortingErUfullstendig.left()
            } else {
                Unit.right()
            }
        }

        is AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående -> {
            if (uteståendeAvkortingPåSak != null) {
                throw IllegalStateException("Prøver å iverksette søknadsbehandling ${søknadsbehandling.id} uten å håndtere utestående avkorting for sak $id")
            }
            Unit.right()
        }

        is AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere -> {
            throw IllegalStateException("Søknadsbehandling ${søknadsbehandling.id} i tilstand ${søknadsbehandling::class} skulle hatt håndtert eventuell avkorting.")
        }
    }
}

private fun saksbehandlerOgAttestantErLike(
    søknadsbehandling: Søknadsbehandling.TilAttestering,
    attestering: Attestering,
): Boolean = søknadsbehandling.saksbehandler.navIdent == attestering.attestant.navIdent
