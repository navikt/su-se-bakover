package no.nav.su.se.bakover.domain.revurdering.opphør

/**
 * Se javadoc til [OpphørsperiodeForUtbetalinger].
 *
 * Vi sender ikke avkortingsmånedene til oppdrag.
 * Alle månedene i revurderingsperioden fører til avkorting, så vi må lage et opphørsvedtak uten utbetaling.
 */
data object AvkortingsopphørUtenUtbetalingslinjer
