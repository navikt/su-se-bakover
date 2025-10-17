package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import satser.domain.SatsFactory
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
    satsFactory: SatsFactory,
    fritekst: String?,
    genererPdf: (command: IverksettSøknadsbehandlingDokumentCommand) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
): Either<KunneIkkeIverksetteSøknadsbehandling, IverksattAvslåttSøknadsbehandlingResponse> {
    require(this.søknadsbehandlinger.any { it == søknadsbehandling })

    val iverksattBehandling = søknadsbehandling.iverksett(attestering, fritekst)
    val vedtak: Avslagsvedtak = opprettAvslagsvedtak(iverksattBehandling, clock)

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

    return IverksattAvslåttSøknadsbehandlingResponse(
        sak = this.oppdaterSøknadsbehandling(iverksattBehandling)
            .copy(
                vedtakListe = this.vedtakListe + vedtak,
            ),
        dokument = dokument,
        vedtak = vedtak,
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
