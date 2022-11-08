package no.nav.su.se.bakover.domain.revurdering.opphør

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

/**
 * Ideélt sett bør det heller sendes en hendelse om et nytt stønadsvedtak på saken (sakId).
 * Kontrollsamtale bør hente gjeldende vedtak/stønad for saken på nytt og ta en ny avgjørelse basert på dette.
 */
fun interface AnnullerKontrollsamtaleVedOpphørService {
    /**
     * Ikke legg på returtyper på denne, siden det er en fire or fail.
     * Dersom det gjøres må de som kaller funksjonen håndtere det i tilfelle det må gjøres en throw for å bryte transaksjonen.
     */
    fun annuller(
        sakId: UUID,
        sessionContext: SessionContext,
    )
}
