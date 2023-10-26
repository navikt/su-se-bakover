package no.nav.su.se.bakover.domain.attestering

import no.nav.su.se.bakover.common.domain.attestering.UnderkjennAttesteringsgrunn

/**
 * Dette er valgene som kan tas, dersom man vil underkjenne
 * [no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling],
 * [no.nav.su.se.bakover.domain.revurdering.Revurdering],
 * [no.nav.su.se.bakover.domain.klage.Klage]
 *
 * Disse feltene passer ikke ved klage. Disse vil vi endre på sikt. Må ta høyde for dette under databasen
 */
enum class UnderkjennAttesteringsgrunnBehandling : UnderkjennAttesteringsgrunn {
    INNGANGSVILKÅRENE_ER_FEILVURDERT,
    BEREGNINGEN_ER_FEIL,
    DOKUMENTASJON_MANGLER,
    VEDTAKSBREVET_ER_FEIL,
    ANDRE_FORHOLD,
}
