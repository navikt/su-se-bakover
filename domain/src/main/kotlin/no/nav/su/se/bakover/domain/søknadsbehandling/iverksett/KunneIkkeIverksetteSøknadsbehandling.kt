package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet

sealed interface KunneIkkeIverksetteSøknadsbehandling {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteSøknadsbehandling
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteSøknadsbehandling
    data class KunneIkkeGenerereVedtaksbrev(val underliggendeFeil: KunneIkkeLageDokument) : KunneIkkeIverksetteSøknadsbehandling
    object AvkortingErUfullstendig : KunneIkkeIverksetteSøknadsbehandling
    object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeIverksetteSøknadsbehandling
    object SimuleringFørerTilFeilutbetaling : KunneIkkeIverksetteSøknadsbehandling

    /**
     * En stønadsperiode kan ikke overskrive utbetalte måneder eller måneder som kommer til å bli utbetalt.
     * I utgangspunk
     * Et unntak er utbetalte måneder som er tilbakekrevd i sin helhet.
     */
    object OverskriverVedtak : KunneIkkeIverksetteSøknadsbehandling
}
