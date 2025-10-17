package no.nav.su.se.bakover.statistikk.behandling

/**
 * Alle behandlinger som er ferdigbehandlet skal ha et resultat.
 * Helt greit å legge på resultater på det tidspunktet en vurdering gir et klart resultat, selvom det er mellomtilstand.
 * Må sees i kombinasjon med [BehandlingStatus]
 */
internal enum class BehandlingResultat(val value: String, val beskrivelse: String) {
    Innvilget(
        value = "INNVILGET",
        beskrivelse = "Behandlingen har blitt innvilget. Dette gjelder søknadsbehandling, revurdering og regulering.",
    ),
    AvslåttSøknadsbehandling(
        value = "AVSLÅTT",
        beskrivelse = "Gjelder kun søknadsbehandling: I henhold til lov om supplerende stønad blir en søknad avslått. Tilsvarende resultat for revurdering er opphørt.",
    ),
    Opphør(
        value = "OPPHØRT",
        beskrivelse = "En revurdering blir opphørt, mens en søknadsbehandling blir avslått.",
    ),
    Stanset(
        value = "STANSET",
        beskrivelse = "Stønadsendring som fører til stans av utbetalingen(e) for gitt(e) måned(er). Det motsatte av resultatet [GJENOPPTATT].",
    ),
    Gjenopptatt(
        value = "GJENOPPTATT",
        beskrivelse = "Stønadsendring som fører til gjenopptak av utbetaling(e) for en gitt måned og framover. Det motsatte av resultatet [STANSET].",
    ),
    OpprettholdtKlage(
        value = "OPPRETTHOLDT",
        beskrivelse = "Kun brukt i klagebehandling ved oversendelse til klageinstansen.",
    ),
    Avbrutt(
        value = "AVBRUTT",
        beskrivelse = "En paraplybetegnelse for å avbryte/avslutte/lukke en behandling der vi ikke har mer spesifikke data. Spesifiseringer av Avbrutt: [FEILREGISTRERT, TRUKKET, AVVIST].",
    ),
    Bortfalt(
        value = "BORTFALT",
        beskrivelse = "Feilaktig registrert behandling. En spesifisering av [AVBRUTT]",
    ),
    Trukket(
        value = "TRUKKET",
        beskrivelse = "Bruker eller verge/fullmakt har bedt om å trekke søknad/klage. En spesifisering av [AVBRUTT].",
    ),
    Avvist(
        value = "AVVIST",
        beskrivelse = "Avvist pga. bl.a. formkrav. En spesifisering av [AVBRUTT].",
    ),
    ;

    override fun toString() = value
}
