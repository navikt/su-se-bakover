package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock

internal fun Sak.iverksettAvslagSøknadsbehandling(
    søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag,
    attestering: Attestering.Iverksatt,
    clock: Clock,
    // TODO jah: Burde kunne lage en brevrequest uten å gå via service-funksjon
    lagDokument: (visitable: Visitable<LagBrevRequestVisitor>) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattAvslåttSøknadsbehandlingResponse> {
    val iverksattBehandling = søknadsbehandling.iverksett(attestering)
    val vedtak: Avslagsvedtak = opprettAvslagsvedtak(iverksattBehandling, clock)

    val dokument = lagDokument(vedtak)
        .getOrHandle { return KunneIkkeIverksetteSøknadsbehandling.KunneIkkeGenerereVedtaksbrev(it).left() }
        .leggTilMetadata(
            Dokument.Metadata(
                sakId = vedtak.behandling.sakId,
                søknadId = null,
                vedtakId = vedtak.id,
                revurderingId = null,
                bestillBrev = true,
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
    iverksattBehandling: Søknadsbehandling.Iverksatt.Avslag,
    clock: Clock,
): Avslagsvedtak {
    return when (iverksattBehandling) {
        is Søknadsbehandling.Iverksatt.Avslag.MedBeregning -> {
            Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
                avslag = iverksattBehandling,
                clock = clock,
            )
        }

        is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning -> {
            Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
                avslag = iverksattBehandling,
                clock = clock,
            )
        }
    }
}
