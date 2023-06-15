package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KanOppdaterePeriodeBosituasjonVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock

/**
 * Tar søknadsbehandlingen kun fram til attestert for å kunne gjenbruke IverksettSøknadsbehandlingService
 */
fun Sak.avslåSøknadPgaManglendeDokumentasjon(
    command: AvslåManglendeDokumentasjonCommand,
    clock: Clock,
    satsFactory: SatsFactory,
    lagDokument: (visitable: Visitable<LagBrevRequestVisitor>) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, periode: Periode) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
): Either<KunneIkkeAvslåSøknad, IverksattAvslåttSøknadsbehandlingResponse> {
    val søknadId = command.søknadId
    return this.hentSøknadsbehandlingForSøknad(søknadId).fold(
        {
            this.opprettNySøknadsbehandling(
                søknadId = command.søknadId,
                clock = clock,
                saksbehandler = command.saksbehandler,
            ).getOrElse { return KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling(it).left() }.let {
                Pair(it.first, it.third)
            }
        },
        {
            Pair(this, it)
        },
    ).let { (sak, behandling) ->
        avslå(
            sak = sak,
            søknadsbehandling = (behandling as? KanOppdaterePeriodeBosituasjonVilkår).let {
                it
                    ?: throw IllegalArgumentException("Søknadsbehandling var ikke av typen KanOppdaterePeriodeGrunnlagVilkår ved avslag pga. manglende dokumentasjon. Actual: ${behandling::class.simpleName} ")
            },
            request = command,
            clock = clock,
            satsFactory = satsFactory,
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
                lagDokument = lagDokument,
                simulerUtbetaling = simulerUtbetaling,
                clock = clock,
            ).map {
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
    satsFactory: SatsFactory,
): Either<KunneIkkeAvslåSøknad, Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning>> {
    // TODO jah: Vi burde gå via sak i alle stegene vi muterer søknadsbehandlingen.
    return søknadsbehandling
        // Dersom en søknadsbehandling kun er opprettet, men stønadsperiode ikke er valgt enda. Dette vil implisitt legge på opplysningspliktvilkåret.
        .leggTilStønadsperiodeOgAldersvurderingHvisNull(
            clock = clock,
            satsFactory = satsFactory,
        ).avslåPgaOpplysningsplikt(
            saksbehandler = request.saksbehandler,
            tidspunkt = Tidspunkt.now(clock),
        ).getOrElse {
            return KunneIkkeAvslåSøknad.Periodefeil(it).left()
        }.tilAttestering(fritekstTilBrev = request.fritekstTilBrev).let { søknadsbehandlingTilAttestering ->

            søknadsbehandlingTilAttestering.getOrElse {
                return KunneIkkeAvslåSøknad.HarValideringsfeil(it).left()
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
    satsFactory: SatsFactory,
): KanOppdaterePeriodeBosituasjonVilkår {
    if (stønadsperiode != null) return this

    return leggTilAldersvurderingOgStønadsperiodeForAvslagPgaManglendeDokumentasjon(
        aldersvurdering = Aldersvurdering.SkalIkkeVurderes(
            Stønadsperiode.create(Måned.now(clock)),
        ),
        formuegrenserFactory = satsFactory.formuegrenserFactory,
        clock = clock,
    )
}
