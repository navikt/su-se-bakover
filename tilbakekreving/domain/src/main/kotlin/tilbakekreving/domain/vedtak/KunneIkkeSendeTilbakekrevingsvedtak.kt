package tilbakekreving.domain.vedtak

/**
 * Dersom vi ikke kunne sende kravgrunnlaget (for å avgjøre om feilutbetalingen skulle føre til tilbakekreving eller ikke) til økonomisystemet
 */
sealed interface KunneIkkeSendeTilbakekrevingsvedtak {
    data object AlvorlighetsgradFeil : KunneIkkeSendeTilbakekrevingsvedtak
    data object FeilStatusFraOppdrag : KunneIkkeSendeTilbakekrevingsvedtak
    data object UkjentFeil : KunneIkkeSendeTilbakekrevingsvedtak
    data object KlarteIkkeHenteSamlToken : KunneIkkeSendeTilbakekrevingsvedtak
    data object KlarteIkkeSerialisereRequest : KunneIkkeSendeTilbakekrevingsvedtak
}
