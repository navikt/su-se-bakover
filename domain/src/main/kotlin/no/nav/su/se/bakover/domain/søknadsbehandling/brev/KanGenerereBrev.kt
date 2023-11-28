@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Søknadsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import sats.domain.SatsFactory

sealed interface KanGenerereBrev : Søknadsbehandling {
    /**
     * Utføres av saksbehandler før attestering, eller dersom den er underkjent.
     */
    fun lagBrevutkastCommandForSaksbehandler(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): IverksettSøknadsbehandlingDokumentCommand

    /**
     * Utføres av attestant under attestering..
     */
    fun lagBrevutkastCommandForAttestant(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Attestant,
    ): IverksettSøknadsbehandlingDokumentCommand

    /**
     * Kjøres en gang når søknadsbehandlingen er iverksatt.
     */
    fun lagBrevCommand(
        satsFactory: SatsFactory,
    ): IverksettSøknadsbehandlingDokumentCommand
}
