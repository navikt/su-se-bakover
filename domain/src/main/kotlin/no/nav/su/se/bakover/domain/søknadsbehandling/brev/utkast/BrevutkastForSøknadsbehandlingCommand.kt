package no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId

sealed interface BrevutkastForSøknadsbehandlingCommand {
    val søknadsbehandlingId: SøknadsbehandlingId
    val utførtAv: NavIdentBruker

    /**
     * Brukes av saksbehandler før hen sen sender til attestering.
     */
    data class ForSaksbehandler(
        override val søknadsbehandlingId: SøknadsbehandlingId,
        override val utførtAv: NavIdentBruker.Saksbehandler,
        val fritekst: String,
    ) : BrevutkastForSøknadsbehandlingCommand

    /**
     * Brukes av attestant når hen skal se på et vedtaksutkast.
     */
    data class ForAttestant(
        override val søknadsbehandlingId: SøknadsbehandlingId,
        override val utførtAv: NavIdentBruker.Attestant,
        val fritekst: String,
    ) : BrevutkastForSøknadsbehandlingCommand
}
