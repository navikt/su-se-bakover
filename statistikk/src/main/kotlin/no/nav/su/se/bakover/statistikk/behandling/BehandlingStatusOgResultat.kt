package no.nav.su.se.bakover.statistikk.behandling

/**
 * Kombinasjon av behandlingsfeltene: (resultat, resultatBegrunnelse og resultatBegrunnelseBeskrivelse) og (behandlingStatus og behandlingStatusBeskrivelse)
 * Beskrivelsene er ikke viktige i utgangspunktet.
 * Alle feltene er null, slik at vi slipper unna med færrest mulig type-kombinasjoner siden dette bare er et utgående lag.
 */
internal data class BehandlingStatusOgResultat(
    val resultat: no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Resultat,
    val status: no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Status,
) {
    constructor(status: no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Status) : this(
        resultat = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Resultat(),
        status = status,
    )

    data class Resultat(
        val resultat: no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat? = null,
        val begrunnelse: String? = null,
        val begrunnelseBeskrivelse: String? = null,
    ) {
        val beskrivelse: String? = resultat?.beskrivelse
    }

    data class Status(
        val status: no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus,
    ) {
        val beskrivelse: String = status.beskrivelse
    }
}
