package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgBehandling
import java.util.UUID

data class AvslagSøknadCmd(
    val søknadId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String,
    val brevvalgSøknadsbehandling: BrevvalgBehandling.Valgt,
)
