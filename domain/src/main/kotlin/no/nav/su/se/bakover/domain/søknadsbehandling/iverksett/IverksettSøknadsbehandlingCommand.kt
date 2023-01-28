package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import no.nav.su.se.bakover.domain.behandling.Attestering
import java.util.UUID

/**
 * @param saksbehandlerOgAttestantKanIkkeVæreDenSamme Ved avslag pga. manglende dokumentasjon vil saksbehandler og attestant være den samme.
 */
data class IverksettSøknadsbehandlingCommand(
    val behandlingId: UUID,
    val attestering: Attestering.Iverksatt,
    // TODO jah: Fjern etter vi flytter avslagManglendeDokumentasjon inn i den normale saksbehandlingsflyten.
    val saksbehandlerOgAttestantKanIkkeVæreDenSamme: Boolean = true,
)
