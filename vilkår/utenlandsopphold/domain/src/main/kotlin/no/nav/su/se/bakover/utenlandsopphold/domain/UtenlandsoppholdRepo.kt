package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.util.UUID

interface UtenlandsoppholdRepo {
    fun lagre(hendelse: RegistrerUtenlandsoppholdHendelse)
    fun lagre(hendelse: KorrigerUtenlandsoppholdHendelse)
    fun lagre(hendelse: AnnullerUtenlandsoppholdHendelse)
    fun hentForSakId(
        sakId: UUID,
        sessionContext: SessionContext = defaultSessionContext(),
    ): UtenlandsoppholdHendelser
    fun defaultSessionContext(): SessionContext
}
