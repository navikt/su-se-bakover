package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.util.UUID

data class AvslagSøknadCmd(
    val søknadId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String = "",
)
