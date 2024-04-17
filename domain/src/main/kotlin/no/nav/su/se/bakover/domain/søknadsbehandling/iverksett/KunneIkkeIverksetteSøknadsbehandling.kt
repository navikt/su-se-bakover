package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.StøtterIkkeOverlappendeStønadsperioder

sealed interface KunneIkkeIverksetteSøknadsbehandling {
    data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteSøknadsbehandling
    data class KunneIkkeGenerereVedtaksbrev(
        val underliggendeFeil: KunneIkkeLageDokument,
    ) : KunneIkkeIverksetteSøknadsbehandling

    data object SimuleringFørerTilFeilutbetaling : KunneIkkeIverksetteSøknadsbehandling

    /**
     * En stønadsperiode kan ikke overlappe tidligere stønadsperioder som har utbetalte måneder eller måneder som kommer til å bli utbetalt.
     * I.e. Kan kun overlappe opphørte måneder som ikke førte til feilutbetaling
     * Mulig utvidelse på sikt kan f.eks. være: Utbetalte måneder som er tilbakekrevd i sin helhet, men det støttes ikke per tidspunkt.
     */
    data class OverlappendeStønadsperiode(
        val underliggendeFeil: StøtterIkkeOverlappendeStønadsperioder,
    ) : KunneIkkeIverksetteSøknadsbehandling

    data class KontrollsimuleringFeilet(val underliggende: no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollsimuleringFeilet) : KunneIkkeIverksetteSøknadsbehandling
}
