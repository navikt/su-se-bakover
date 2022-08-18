package no.nav.su.se.bakover.statistikk.behandling

/**
 * Statistikk skiller på status og resultat, hvor vi ofte har slått de sammen i en stor enum/sealed.
 */
internal enum class BehandlingStatus(val value: String, val beskrivelse: String) {
    Registrert(
        value = "REGISTRERT",
        beskrivelse = "Vi har registrert en søknad, klage, revurdering, stans, gjenopptak eller lignende i systemet. Mottatt tidspunkt kan ha skjedd på et tidligere tidspunkt, som f.eks. ved papirsøknad og klage.",
    ),
    UnderBehandling(
        value = "UNDER_BEHANDLING",
        beskrivelse = "Et mellomsteg i behandlingen.",
    ),
    TilAttestering(
        value = "TIL_ATTESTERING",
        beskrivelse = "Saksbehandler har sendt behandlingen videre til beslutter/attestant/saksbehandler2 som må velge og enten underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandler) den.",
    ),
    Underkjent(
        value = "UNDERKJENT",
        beskrivelse = "beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler.",
    ),
    Avsluttet(
        value = "AVSLUTTET",
        beskrivelse = "Behandlingen/søknaden har blitt avsluttet/lukket.",
    ),
    Iverksatt(
        value = "IVERKSATT",
        beskrivelse = "Behandlingen har blitt iverksatt.",
    ),
    OversendtKlage(
        value = "OVERSENDT",
        beskrivelse = "Oversendt innstilling til klageinstansen. Denne er unik for klage. Brukes f.eks. ved resultatet [OPPRETTHOLDT].",
    ),
    ;

    override fun toString() = value
}

// TODO jah: Burde kanskje legge til statusen venter på svar når vi sender forhåndvarsel ved revurdering.
