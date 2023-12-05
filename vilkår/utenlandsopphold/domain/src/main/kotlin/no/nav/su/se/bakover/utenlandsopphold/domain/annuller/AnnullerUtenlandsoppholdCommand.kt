package no.nav.su.se.bakover.utenlandsopphold.domain.annuller

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

data class AnnullerUtenlandsoppholdCommand(
    val sakId: UUID,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: List<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val annullererVersjon: Hendelsesversjon,
) {
    /**
     * @param nesteVersjon Versjonen den nye hendelsen skal få.
     */
    fun toHendelse(
        annullererHendelse: UtenlandsoppholdHendelse,
        nesteVersjon: Hendelsesversjon,
        clock: Clock,
    ): AnnullerUtenlandsoppholdHendelse {
        return AnnullerUtenlandsoppholdHendelse.create(
            annullererHendelse = annullererHendelse,
            nesteVersjon = nesteVersjon,
            utførtAv = opprettetAv,
            clock = clock,
        )
    }

    fun toMetadata(): DefaultHendelseMetadata {
        return DefaultHendelseMetadata(
            correlationId = correlationId,
            ident = opprettetAv,
            brukerroller = brukerroller,
        )
    }
}
