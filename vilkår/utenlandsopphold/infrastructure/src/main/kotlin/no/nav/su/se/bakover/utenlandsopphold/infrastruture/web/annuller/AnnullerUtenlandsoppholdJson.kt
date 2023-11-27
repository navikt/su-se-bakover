package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.annuller

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdCommand
import java.util.UUID

data class AnnullerUtenlandsoppholdJson(
    val saksversjon: Long,
) {
    fun toCommand(
        sakId: UUID,
        opprettetAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
        annullererVersjon: Long,
    ) = AnnullerUtenlandsoppholdCommand(
        sakId = sakId,
        opprettetAv = opprettetAv,
        correlationId = correlationId,
        brukerroller = brukerroller,
        klientensSisteSaksversjon = Hendelsesversjon(saksversjon),
        annullererVersjon = Hendelsesversjon(annullererVersjon),
    )
}
