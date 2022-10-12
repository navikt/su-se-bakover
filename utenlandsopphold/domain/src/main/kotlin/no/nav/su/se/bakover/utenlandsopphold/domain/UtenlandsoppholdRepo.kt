package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.OppdaterUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.util.UUID

interface UtenlandsoppholdRepo {
    fun lagre(hendelse: RegistrerUtenlandsoppholdHendelse)
    fun lagre(hendelse: OppdaterUtenlandsoppholdHendelse)
    fun hentForSakId(
        sakId: UUID,
        sessionContext: SessionContext = defaultSessionContext(),
    ): List<RegistrertUtenlandsopphold>

    fun hentSisteHendelse(sakId: UUID, utenlandsoppholdId: UUID): UtenlandsoppholdHendelse?
    fun defaultSessionContext(): SessionContext
}
