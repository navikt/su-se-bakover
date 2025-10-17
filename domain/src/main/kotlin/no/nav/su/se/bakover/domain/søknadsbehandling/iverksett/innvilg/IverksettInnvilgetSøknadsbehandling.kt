package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.innvilg

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.oppdrag.simulering.kontrollsimuler
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.sak.sisteVedtakstidpunkt
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.fromSøknadsbehandlingInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattInnvilgetSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.validerOverlappendeStønadsperioder
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock

private val log = LoggerFactory.getLogger("IverksettInnvilgetSøknadsbehandling.kt")

/**
 * Innvilger søknadsbehandlingen uten sideeffekter.
 * IO: Utbetalingen simules.
 *
 * Begrensninger:
 * - Det kan ikke finnes åpne kravgrunnlag på saken.
 * - Kan ikke føre til feilutbetaling (verifiseres vha. simulering og kontrollsimulering)
 * - Stønadsperioden kan ikke overlappe tidligere stønadsperioder, med noen unntak:
 *     - Opphørte måneder som ikke har ført til utbetaling kan "overskrives".
 *     - Opphørte måneder med feilutbetaling som har blitt 100% tilbakekrevet kan "overskrives".
 */
internal fun Sak.iverksettInnvilgetSøknadsbehandling(
    søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget,
    attestering: Attestering.Iverksatt,
    clock: Clock,
    satsFactory: SatsFactory,
    fritekst: String?,
    simulerUtbetaling: (utbetalingForSimulering: Utbetaling.UtbetalingForSimulering) -> Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling>,
    genererPdf: (command: IverksettSøknadsbehandlingDokumentCommand) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattInnvilgetSøknadsbehandlingResponse> {
    require(this.søknadsbehandlinger.any { it == søknadsbehandling })

    either {
        // John Are's vurdering: I utgangspunktet kan sjekken være der, det er kun når vi tar en sak med sivilstandsendring at vi kan komme borti feilutbetalinger og i de tilfellene så virker sjekken slik den skal.
        validerFeilutbetalinger(søknadsbehandling).bind()
        validerGjeldendeVedtak(søknadsbehandling).bind()
        validerBeregningstidspunkt(søknadsbehandling).bind()
    }.onLeft {
        return it.left()
    }

    val iverksattBehandling = søknadsbehandling.tilIverksatt(attestering, fritekst)
    val simulertUtbetaling = this.lagNyUtbetaling(
        saksbehandler = attestering.attestant,
        beregning = iverksattBehandling.beregning,
        clock = clock,
        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        uføregrunnlag = iverksattBehandling.hentUføregrunnlag(),
    ).let {
        kontrollsimuler(
            utbetalingForSimulering = it,
            simuler = simulerUtbetaling,
            saksbehandlersSimulering = iverksattBehandling.simulering,
        )
    }.getOrElse {
        log.error("Kunne ikke iverksette innvilget søknadsbehandling ${iverksattBehandling.id}. Underliggende feil:$it.")
        return KunneIkkeIverksetteSøknadsbehandling.KontrollsimuleringFeilet(it).left()
    }
    val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandlingInnvilget(
        søknadsbehandling = iverksattBehandling,
        utbetalingId = simulertUtbetaling.id,
        clock = clock,
    )

    val dokument = genererPdf(vedtak.behandling.lagBrevCommand(satsFactory))
        .getOrElse { return KunneIkkeIverksetteSøknadsbehandling.KunneIkkeGenerereVedtaksbrev(it).left() }
        .leggTilMetadata(
            Dokument.Metadata(
                sakId = vedtak.behandling.sakId,
                søknadId = null,
                vedtakId = vedtak.id,
                revurderingId = null,
            ),
            // kan ikke sende vedtaksbrev til en annen adresse enn brukerens adresse per nå
            distribueringsadresse = null,
        )

    val oppdatertSak = this.oppdaterSøknadsbehandling(iverksattBehandling).copy(
        vedtakListe = this.vedtakListe + vedtak,
        utbetalinger = this.utbetalinger + simulertUtbetaling,
    )
    return IverksattInnvilgetSøknadsbehandlingResponse(
        sak = oppdatertSak,
        vedtak = vedtak,
        dokument = dokument,
        statistikk = nonEmptyListOf(
            StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak),
        ),
        utbetaling = simulertUtbetaling,
    ).right()
}

private fun Sak.validerGjeldendeVedtak(
    søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget,
): Either<KunneIkkeIverksetteSøknadsbehandling.OverlappendeStønadsperiode, Unit> {
    return this.validerOverlappendeStønadsperioder(søknadsbehandling.periode).mapLeft {
        KunneIkkeIverksetteSøknadsbehandling.OverlappendeStønadsperiode(it)
    }
}

/**
 * Sjekker kun saksbehandlers simulering.
 */
private fun validerFeilutbetalinger(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget): Either<KunneIkkeIverksetteSøknadsbehandling.SimuleringFørerTilFeilutbetaling, Unit> {
    if (søknadsbehandling.simulering.harFeilutbetalinger()) {
        log.warn("Kan ikke iverksette søknadsbehandling ${søknadsbehandling.id} hvor simulering inneholder feilutbetalinger. Dette er kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer. Se sikkerlogg for simuleringsdetaljer.")
        sikkerLogg.warn("Kan ikke iverksette søknadsbehandling ${søknadsbehandling.id} hvor simulering inneholder feilutbetalinger. Dette er kun en nødbrems for tilfeller som i utgangspunktet skal være håndtert og forhindret av andre mekanismer. Simulering: ${søknadsbehandling.simulering}")
        return KunneIkkeIverksetteSøknadsbehandling.SimuleringFørerTilFeilutbetaling.left()
    }
    return Unit.right()
}

private fun Sak.validerBeregningstidspunkt(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget): Either<KunneIkkeIverksetteSøknadsbehandling.BeregningstidspunktErFørSisteVedtak, Unit> {
    this.sisteVedtakstidpunkt()?.also {
        // Kommentar jah: Denne sjekken kan forbedres. Vi ønsker egentlig at simuleringen under søknadsbehandlingen vi får fra oppdrag skal være basert på siste utbetalingslinje vi har sendt de. Da kan vi plukke opp blant annet feilutbetalinger.
        if (søknadsbehandling.beregning.getOpprettet() <= it) {
            return KunneIkkeIverksetteSøknadsbehandling.BeregningstidspunktErFørSisteVedtak.left()
        }
    }
    return Unit.right()
}
