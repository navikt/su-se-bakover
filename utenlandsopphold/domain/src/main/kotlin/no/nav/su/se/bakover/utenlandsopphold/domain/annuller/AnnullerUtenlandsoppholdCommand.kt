package no.nav.su.se.bakover.utenlandsopphold.domain.annuller

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
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
            hendelseMetadata = HendelseMetadata(
                correlationId = correlationId,
                ident = opprettetAv,
                brukerroller = brukerroller,
            ),
        )
    }
}
