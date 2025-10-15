package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId

/**
 * @param saksbehandlerOgAttestantKanIkkeVæreDenSamme Ved avslag pga. manglende dokumentasjon vil saksbehandler og attestant være den samme.
 */
data class IverksettSøknadsbehandlingCommand(
    val behandlingId: SøknadsbehandlingId,
    val attestering: Attestering.Iverksatt,
    val fritekstTilBrev: String,

    // TODO jah: Fjern etter vi flytter avslagManglendeDokumentasjon inn i den normale saksbehandlingsflyten.
    val saksbehandlerOgAttestantKanIkkeVæreDenSamme: Boolean = true,
)
