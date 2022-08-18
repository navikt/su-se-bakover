package no.nav.su.se.bakover.statistikk.behandling

/**
 * Alle behandlinger som er ferdigbehandlet av førsteinstansen bør ha et resultat.
 * Må sees i kombinasjon med [BehandlingStatus]
 */
internal enum class BehandlingResultat(val beskrivelse: String) {
    Innvilget("Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering."),
    Avslag("Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt."),
    Opphør("En revurdering kan opphøre, mens en søknadsbehandling kan bli avslått."),
    Stans("Stønadsendring som fører til stans av utbetalingen(e) for en gitt måned og framover. Det motsatte av resultatet [Gjenopptatt]."),

    // TODO jah: Finner ikke denne i standarden
    Gjenopptatt("Stønadsendring som fører til gjenopptak av utbetaling(e) for en gitt måned og framover. Det motsatte av resultatet [Stanset]."),

    // TODO jah: Finner ikke denne i standarden
    Opprettholdt("Kun brukt i klagebehandling ved oversendelse til klageinstansen."),
    Avbrutt("En paraplybetegnelse for å avbryte/avslutte/lukke en behandling der vi ikke har mer spesifikke data. Dekker bl.a.: [Bortfalt,Feilregistrert,Henlagt,Trukket,Avvist]."),

    // TODO jah: Er dette det samme som henlagt eller feilregistrert eller ikke realitetsbehandlet?
    Bortfalt("Spesifisering av [Avbrutt]."),
    Trukket("Spesifisering av [Avbrutt]. Bruker eller verge/fullmakt har bedt om å trekke søknad/klage."),
    Avvist("Spesifisering av [Avbrutt]. F.eks. formkrav."),
}
