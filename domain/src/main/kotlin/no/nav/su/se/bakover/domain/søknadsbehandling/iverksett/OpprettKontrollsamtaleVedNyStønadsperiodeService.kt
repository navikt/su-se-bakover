package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling

/**
 * Ideélt sett bør det heller sendes en hendelse om et nytt stønadsvedtak på saken (sakId).
 * Også bør kontrollsamtale hente gjeldende vedtak/stønad for saken på nytt og ta en ny avgjørelse basert på dette.
 */
interface OpprettKontrollsamtaleVedNyStønadsperiodeService {
    /**
     * Ikke legg på returtyper på denne, siden det er en fire or fail.
     * Dersom det gjøres må de som kaller funksjonen håndtere det i tilfelle det må gjøres en throw for å bryte transaksjonen.
     */
    fun opprett(
        vedtak: VedtakInnvilgetSøknadsbehandling,
        sessionContext: SessionContext,
    )
}
