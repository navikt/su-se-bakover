package no.nav.su.se.bakover.domain.behandling

/**
 * En spesifisering av avsluttet - Avbrutt er for når en behanadling blir startet, men senere viser seg at
 * det ikke er noe behov for behandlingen.
 * <p>
 * Eksempel er [no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering]
 */
interface Avbrutt : Avsluttet
