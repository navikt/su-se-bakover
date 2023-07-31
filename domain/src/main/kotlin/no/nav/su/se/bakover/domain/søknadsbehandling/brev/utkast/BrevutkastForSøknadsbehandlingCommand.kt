package no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.util.UUID

sealed interface BrevutkastForSøknadsbehandlingCommand {
    val søknadsbehandlingId: UUID
    val utførtAv: NavIdentBruker

    /**
     * Brukes av saksbehandler før hen sen sender til attestering.
     */
    data class ForSaksbehandler(
        override val søknadsbehandlingId: UUID,
        override val utførtAv: NavIdentBruker.Saksbehandler,
        val fritekst: String,
    ) : BrevutkastForSøknadsbehandlingCommand

    /**
     * Brukes av attestant når hen skal se på et vedtaksutkast.
     */
    data class ForAttestant(
        override val søknadsbehandlingId: UUID,
        override val utførtAv: NavIdentBruker.Attestant,
    ) : BrevutkastForSøknadsbehandlingCommand
}
