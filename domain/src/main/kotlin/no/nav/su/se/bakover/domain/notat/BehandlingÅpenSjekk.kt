package no.nav.su.se.bakover.domain.notat

import arrow.core.Either
import java.util.UUID

/**
 * Sjekker status på en behandling/søknad/klage for å avgjøre om et notat kan endres.
 * Brukes av [NotatService] i stedet for direkte avhengigheter mot RevurderingService,
 * SøknadsbehandlingService, KlageService og SøknadService — noe som ville skapt
 * en sirkulær avhengighet via JournalførVedtaksnotatService.
 */
interface BehandlingÅpenSjekk {
    fun hentStatus(referanseId: UUID, referanseType: ReferanseType): Either<NotatFeil, BehandlingStatus>
}

data class BehandlingStatus(
    val erÅpen: Boolean,
    val erTilAttestering: Boolean,
)
