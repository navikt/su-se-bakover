package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.KanOppdaterePeriodeBosituasjonVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import satser.domain.SatsFactory
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock

/**
 * Tar søknadsbehandlingen kun fram til attestert for å kunne gjenbruke IverksettSøknadsbehandlingService
 */
fun Sak.avslåSøknadPgaManglendeDokumentasjon(
    command: AvslåManglendeDokumentasjonCommand,
    clock: Clock,
    satsFactory: SatsFactory,
    formuegrenserFactory: FormuegrenserFactory,
    genererPdf: (IverksettSøknadsbehandlingDokumentCommand.Avslag) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    lukkOppgave: (oppgaveId: OppgaveId) -> Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse>,
): Either<KunneIkkeAvslåSøknad, IverksattAvslåttSøknadsbehandlingResponse> {
    val søknadId = command.søknadId
    return this.hentSøknadsbehandlingForSøknad(søknadId).fold(
        {
            this.opprettNySøknadsbehandling(
                søknadId = command.søknadId,
                clock = clock,
                saksbehandler = command.saksbehandler,
                oppdaterOppgave = null,
            ).getOrElse { return KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling(it).left() }.let {
                Pair(it.first, listOf(it.second))
            }
        },
        {
            Pair(this, it)
        },
    ).let { (sak: Sak, behandlinger: List<Søknadsbehandling>) ->
        behandlinger.filterIsInstance<KanOppdaterePeriodeBosituasjonVilkår>().whenever(
            { throw IllegalArgumentException("Avslag pga manglende dok. Fant ingen søknadsbehandling, eller Søknadsbehandling var ikke av typen KanOppdaterePeriodeGrunnlagVilkår for sak ${sak.id}, søknad ${command.søknadId}") },
            {
                avslå(
                    sak = sak,
                    søknadsbehandling = it.single(),
                    request = command,
                    clock = clock,
                    formuegrenserFactory = formuegrenserFactory,
                )
            },
        )
    }.let {
        it.getOrElse {
            return it.left()
        }.let { sakOgBehandling ->
            sakOgBehandling.first.iverksettSøknadsbehandling(
                command = IverksettSøknadsbehandlingCommand(
                    behandlingId = sakOgBehandling.second.id,
                    attestering = Attestering.Iverksatt(
                        attestant = NavIdentBruker.Attestant(command.saksbehandler.navIdent),
                        opprettet = Tidspunkt.now(clock),
                    ),
                    // For avslag pga. manglende dokumentasjon vil saksbehandler og attestant være den samme.
                    saksbehandlerOgAttestantKanIkkeVæreDenSamme = false,
                ),
                genererPdf = { genererPdf(it as IverksettSøknadsbehandlingDokumentCommand.Avslag) },
                simulerUtbetaling = simulerUtbetaling,
                clock = clock,
                satsFactory = satsFactory,
            ).map {
                // best effort for å lukke oppgaven
                lukkOppgave(it.søknadsbehandling.oppgaveId)
                it as IverksattAvslåttSøknadsbehandlingResponse
            }.mapLeft { KunneIkkeAvslåSøknad.KunneIkkeIverksetteSøknadsbehandling(it) }
        }
    }
}

private fun avslå(
    sak: Sak,
    søknadsbehandling: KanOppdaterePeriodeBosituasjonVilkår,
    request: AvslåManglendeDokumentasjonCommand,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
): Either<KunneIkkeAvslåSøknad, Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning>> {
    // TODO jah: Vi burde gå via sak i alle stegene vi muterer søknadsbehandlingen.
    return søknadsbehandling
        // Dersom en søknadsbehandling kun er opprettet, men stønadsperiode ikke er valgt enda. Dette vil implisitt legge på opplysningspliktvilkåret.
        .leggTilStønadsperiodeOgAldersvurderingHvisNull(
            clock = clock,
            formuegrenserFactory = formuegrenserFactory,
        ).avslåPgaOpplysningsplikt(
            saksbehandler = request.saksbehandler,
            tidspunkt = Tidspunkt.now(clock),
        ).getOrElse {
            return KunneIkkeAvslåSøknad.Periodefeil(it).left()
        }.tilAttesteringForSystembruker(fritekstTilBrev = request.fritekstTilBrev)
        .let { søknadsbehandlingTilAttestering ->

            søknadsbehandlingTilAttestering.getOrElse {
                return KunneIkkeAvslåSøknad.Attesteringsfeil(it).left()
            }.let { søknadsbehandlingTilAttesteringUtenFeil ->
                Pair(
                    sak.oppdaterSøknadsbehandling(søknadsbehandlingTilAttesteringUtenFeil),
                    søknadsbehandlingTilAttesteringUtenFeil,
                ).right()
            }
        }
}

/**
 * Burde kanskje oppdatere via [no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling] men for å unngå problemer i tilfeller der stønadsperiode
 * ikke er valgt av saksbeahndler gjør vi det direkte på søknadsbehandling.
 */
private fun KanOppdaterePeriodeBosituasjonVilkår.leggTilStønadsperiodeOgAldersvurderingHvisNull(
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
): KanOppdaterePeriodeBosituasjonVilkår {
    if (stønadsperiode != null) return this

    return leggTilAldersvurderingOgStønadsperiodeForAvslagPgaManglendeDokumentasjon(
        aldersvurdering = Aldersvurdering.SkalIkkeVurderes(
            Stønadsperiode.create(Måned.now(clock)),
        ),
        formuegrenserFactory = formuegrenserFactory,
        clock = clock,
    )
}
