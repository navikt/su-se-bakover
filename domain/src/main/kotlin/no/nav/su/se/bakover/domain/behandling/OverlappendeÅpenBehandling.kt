package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

/**
 * Typisk brukt ved opprettelse/oppdatering av perioden til en behandling, i.e.:
 * - [no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling]
 * - [no.nav.su.se.bakover.domain.revurdering.Revurdering]
 * - [no.nav.su.se.bakover.domain.regulering.Regulering]
 */
fun Sak.finnesOverlappendeÅpenBehandling(
    periode: Periode,
    ekskluderBehandling: UUID,
): Boolean {
    return finnesOverlappendeÅpenSøknadsbehandling(periode, ekskluderBehandling) ||
        finnesOverlappendeÅpenRevurdering(periode, ekskluderBehandling) ||
        finnesOverlappendeÅpenRegulering(periode, ekskluderBehandling)
}

fun Sak.finnesOverlappendeÅpenSøknadsbehandling(
    periode: Periode,
    ekskluderBehandling: UUID,
): Boolean {
    return periode.overlapper(
        this.hentÅpneSøknadsbehandlinger()
            .filterNot { it.id == ekskluderBehandling }
            .mapNotNull { it.stønadsperiode?.periode },
    )
}

/**
 * Inkluderer revurdering, stans og gjenopptak
 */
fun Sak.finnesOverlappendeÅpenRevurdering(
    periode: Periode,
    ekskluderBehandling: UUID,
): Boolean {
    return periode.overlapper(
        this.hentÅpneRevurderinger()
            .filterNot { it.id == ekskluderBehandling }
            .map { it.periode },
    )
}

fun Sak.finnesOverlappendeÅpenRegulering(
    periode: Periode,
    ekskluderBehandling: UUID,
): Boolean {
    return periode.overlapper(
        this.hentÅpneReguleringer()
            .filterNot { it.id == ekskluderBehandling }
            .map { it.periode },
    )
}
