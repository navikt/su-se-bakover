package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

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
            søknadsbehandling = behandling,
            request = command,
            clock = clock,
            satsFactory = satsFactory,
        )
    }.let {
        it.first.iverksettSøknadsbehandling(
            command = IverksettSøknadsbehandlingCommand(
                behandlingId = it.second.id,
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

private fun avslå(
    sak: Sak,
    søknadsbehandling: Søknadsbehandling,
    request: AvslåManglendeDokumentasjonCommand,
    clock: Clock,
    satsFactory: SatsFactory,
): Pair<Sak, SøknadsbehandlingTilAttestering.Avslag.UtenBeregning> {
    // TODO jah: Vi burde gå via sak i alle stegene vi muterer søknadsbehandlingen.
    return søknadsbehandling
        // Dersom en søknadsbehandling kun er opprettet, men stønadsperiode ikke er valgt enda.
        .leggTilStønadsperiodeOgAldersvurderingHvisNull(
            clock = clock,
            satsFactory = satsFactory,
            avkorting = sak.hentUteståendeAvkortingForSøknadsbehandling().fold({ it }, { it }).kanIkke(),
        )
        .avslåPgaManglendeDokumentasjon(request.saksbehandler, clock)
        .tilAttestering(fritekstTilBrev = request.fritekstTilBrev).let { søknadsbehandlingTilAttestering ->
            Pair(
                sak.copy(
                    søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == søknadsbehandlingTilAttestering.id } + søknadsbehandlingTilAttestering,
                ),
                søknadsbehandlingTilAttestering,
            )
        }
}

/**
 * Burde kanskje oppdatere via [Sak.oppdaterStønadsperiodeForSøknadsbehandling] men for å unngå problemer i tilfeller der stønadsperiode
 * ikke er valgt av saksbeahndler gjør vi det direkte på søknadsbehandling.
 */
private fun Søknadsbehandling.leggTilStønadsperiodeOgAldersvurderingHvisNull(
    clock: Clock,
    satsFactory: SatsFactory,
    avkorting: AvkortingVedSøknadsbehandling,
): Søknadsbehandling {
    if (stønadsperiode != null) return this

    return leggTilAldersvurderingOgStønadsperiodeForAvslagPgaManglendeDokumentasjon(
        aldersvurdering = Aldersvurdering.SkalIkkeVurderes(
            Stønadsperiode.create(
                periode = Periode.create(
                    fraOgMed = LocalDate.now(clock).startOfMonth(),
                    tilOgMed = LocalDate.now(clock).endOfMonth(),
                ),
            ),
        ),
        formuegrenserFactory = satsFactory.formuegrenserFactory,
        clock = clock,
        avkorting = avkorting,
    )
}

private fun Søknadsbehandling.avslåPgaManglendeDokumentasjon(
    saksbehandler: NavIdentBruker.Saksbehandler,
    clock: Clock,
): VilkårsvurdertSøknadsbehandling.Avslag {
    return leggTilOpplysningspliktVilkårForSaksbehandler(
        opplysningspliktVilkår = OpplysningspliktVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeOpplysningsplikt.create(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    grunnlag = Opplysningspliktgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = opprettet,
                        periode = periode,
                        beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                    ),
                ),
            ),
        ).getOrElse { throw IllegalArgumentException(it.toString()) },
        saksbehandler = saksbehandler,
        clock = clock,
    ).getOrElse { throw IllegalArgumentException(it.toString()) } as VilkårsvurdertSøknadsbehandling.Avslag
}
