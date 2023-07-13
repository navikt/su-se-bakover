package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.StøtterIkkeOverlappendeStønadsperioder

sealed interface KunneIkkeIverksetteSøknadsbehandling {
    data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteSøknadsbehandling
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteSøknadsbehandling
    data class KunneIkkeGenerereVedtaksbrev(val underliggendeFeil: KunneIkkeLageDokument) :
        KunneIkkeIverksetteSøknadsbehandling

    data object AvkortingErUfullstendig : KunneIkkeIverksetteSøknadsbehandling
    data object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeIverksetteSøknadsbehandling
    data object SimuleringFørerTilFeilutbetaling : KunneIkkeIverksetteSøknadsbehandling

    /**
     * En stønadsperiode kan ikke overlappe tidligere stønadsperioder som har utbetalte måneder eller måneder som kommer til å bli utbetalt.
     * I.e. Kan kun overlappe opphørte måneder som ikke førte til feilutbetaling eller avkorting.
     * Mulig utvidelse på sikt kan f.eks. være: Utbetalte måneder som er tilbakekrevd i sin helhet, men det støttes ikke per tidspunkt.
     */
    data class OverlappendeStønadsperiode(
        val underliggendeFeil: StøtterIkkeOverlappendeStønadsperioder,
    ) : KunneIkkeIverksetteSøknadsbehandling

    data object InneholderUfullstendigeBosituasjoner : KunneIkkeIverksetteSøknadsbehandling
}
