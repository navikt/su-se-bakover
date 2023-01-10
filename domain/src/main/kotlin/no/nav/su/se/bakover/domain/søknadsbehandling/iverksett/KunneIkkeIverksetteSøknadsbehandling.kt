package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.StøtterIkkeOverlappendeStønadsperioder

sealed interface KunneIkkeIverksetteSøknadsbehandling {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteSøknadsbehandling
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteSøknadsbehandling
    data class KunneIkkeGenerereVedtaksbrev(val underliggendeFeil: KunneIkkeLageDokument) :
        KunneIkkeIverksetteSøknadsbehandling

    object AvkortingErUfullstendig : KunneIkkeIverksetteSøknadsbehandling
    object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeIverksetteSøknadsbehandling
    object SimuleringFørerTilFeilutbetaling : KunneIkkeIverksetteSøknadsbehandling

    /**
     * En stønadsperiode kan ikke overlappe tidligere stønadsperioder som har utbetalte måneder eller måneder som kommer til å bli utbetalt.
     * I.e. Kan kun overlappe opphørte måneder som ikke førte til feilutbetaling eller avkorting.
     * Mulig utvidelse på sikt kan f.eks. være: Utbetalte måneder som er tilbakekrevd i sin helhet, men det støttes ikke per tidspunkt.
     */
    data class OverlappendeStønadsperiode(
        val underliggendeFeil: StøtterIkkeOverlappendeStønadsperioder,
    ) : KunneIkkeIverksetteSøknadsbehandling
}
