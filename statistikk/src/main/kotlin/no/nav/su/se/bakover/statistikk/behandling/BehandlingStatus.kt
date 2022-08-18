package no.nav.su.se.bakover.statistikk.behandling

/**
 * Statistikk skiller på status og resultat, hvor vi ofte har slått de sammen i en stor enum/sealed.
 */
internal enum class BehandlingStatus(val beskrivelse: String) {
    // TODO jah: Rename til MOTTATT ref. standard eller legg på verdi
    SØKNAD_MOTTATT("Søknaden er mottatt og registrert i systemet. En asynkron jobb har blitt startet for å journalføre og lage Gosys-oppgave."),

    // TODO jah: slett SØKNAD_JOURNALFØRT og SØKNAD_OPPRETTET_OPPGAVE ettersom vi ikke faktisk sender statistikk for disse.
    SØKNAD_JOURNALFØRT("Søknaden er journalført. Det er et krav før vi oppretter en saksbehandleroppgave i Gosys."),
    SØKNAD_OPPRETTET_OPPGAVE("Opprettet oppgave i Gosys.. Det er et krav før en saksbehandler kan starte saksbehandlingen."),
    REGISTRERT("Opprettet/registrert behandling. Dette er ofte trigget av en saksbehandler."),
    UNDER_BEHANDLING("Mellomstadie i behandlingen. Ofte informasjonsinnhenting som ikke fører til en mer spesifikk status."),

    // TODO jah: Fjern Beregnet/Simulert og evt. bytt til UNDER_BEHANDLING dersom vi skal sende statistikk for disse stegene.
    BEREGNET("Denne er unik for stønadsendringsbehandlinger (e.g. SØKNADSBEHANDLING, REVURDERING, REGULERING) som sender oppdragslinjer ved iverksettelse."),
    SIMULERT("Denne er unik for stønadsendringsbehandlinger (e.g. SØKNADSBEHANDLING, REVURDERING, REGULERING) som sender oppdragslinjer ved iverksettelse."),
    TIL_ATTESTERING("Saksbehandler har sendt behandlingene videre til beslutter/attestant/saksbehandler2 som må velge å underkjenne(sendes tilbake til saksbehandler) eller iverksette (ferdigbehandlet) den."),
    UNDERKJENT("beslutter/attestant/saksbehandler2 har sendt saken tilbake til saksbehandler."),
    AVSLUTTET("Behandlingen har blitt avsluttet/lukket."),
    IVERKSATT("Behandlingen har blitt iverksatt."),

    // TODO jah: Bytte til INNSTILT (evt. legge den i verdi)
    OVERSENDT("Oversendt til klageinstansen. Denne er unik for klage. Brukes f.eks. ved resultatet [Opprettholdt].");
}

// TODO jah: Burde kanskje legge til statusen venter på svar når vi sender forhåndvarsel ved revurdering.
