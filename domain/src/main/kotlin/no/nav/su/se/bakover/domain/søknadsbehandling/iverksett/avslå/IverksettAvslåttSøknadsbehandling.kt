package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock

/**
 *  Avslår søknadsbehandlingen uten sideeffekter.
 *  IO: Genererer vedtaksbrev.
 *
 * Et avslagsvedtak fører ikke til endring i ytelsen.
 * Derfor vil et avslagsvedtak sin "stønadsperiode" kunne overlappe tidligere avslagsvedtak og andre vedtak som påvirker ytelsen.
 */
internal fun Sak.iverksettAvslagSøknadsbehandling(
    søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag,
    attestering: Attestering.Iverksatt,
    clock: Clock,
    // TODO jah: Burde kunne lage en brevrequest uten å gå via service-funksjon
    lagDokument: (visitable: Visitable<LagBrevRequestVisitor>) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattAvslåttSøknadsbehandlingResponse> {
    require(this.søknadsbehandlinger.any { it == søknadsbehandling })

    val iverksattBehandling = søknadsbehandling.iverksett(attestering).getOrElse {
        return it.left()
    }
    val vedtak: Avslagsvedtak = opprettAvslagsvedtak(iverksattBehandling, clock)

    val dokument = lagDokument(vedtak)
        .getOrElse { return KunneIkkeIverksetteSøknadsbehandling.KunneIkkeGenerereVedtaksbrev(it).left() }
        .leggTilMetadata(
            Dokument.Metadata(
                sakId = vedtak.behandling.sakId,
                søknadId = null,
                vedtakId = vedtak.id,
                revurderingId = null,
            ),
        )

    return IverksattAvslåttSøknadsbehandlingResponse(
        sak = this.copy(
            vedtakListe = this.vedtakListe + vedtak,
            søknadsbehandlinger = this.søknadsbehandlinger.filterNot { it.id == iverksattBehandling.id } + iverksattBehandling,
        ),
        dokument = dokument,
        vedtak = vedtak,
        statistikkhendelse = StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(vedtak),
        oppgaveSomSkalLukkes = søknadsbehandling.oppgaveId,
    ).right()
}

private fun opprettAvslagsvedtak(
    iverksattBehandling: IverksattSøknadsbehandling.Avslag,
    clock: Clock,
): Avslagsvedtak {
    return when (iverksattBehandling) {
        is IverksattSøknadsbehandling.Avslag.MedBeregning -> {
            Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
                avslag = iverksattBehandling,
                clock = clock,
            )
        }

        is IverksattSøknadsbehandling.Avslag.UtenBeregning -> {
            Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
                avslag = iverksattBehandling,
                clock = clock,
            )
        }
    }
}
