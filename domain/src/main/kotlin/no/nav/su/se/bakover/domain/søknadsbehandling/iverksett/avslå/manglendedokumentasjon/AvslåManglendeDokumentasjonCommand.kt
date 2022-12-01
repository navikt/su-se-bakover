package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon

import no.nav.su.se.bakover.common.NavIdentBruker
import java.util.UUID

data class AvslåManglendeDokumentasjonCommand(
    val søknadId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String = "",
)
