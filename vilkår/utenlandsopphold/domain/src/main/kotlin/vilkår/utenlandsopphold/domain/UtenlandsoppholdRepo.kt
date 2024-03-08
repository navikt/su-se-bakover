package vilkår.utenlandsopphold.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import vilkår.utenlandsopphold.domain.annuller.AnnullerUtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import vilkår.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import java.util.UUID

interface UtenlandsoppholdRepo {
    fun lagre(hendelse: RegistrerUtenlandsoppholdHendelse, meta: DefaultHendelseMetadata)
    fun lagre(hendelse: KorrigerUtenlandsoppholdHendelse, meta: DefaultHendelseMetadata)
    fun lagre(hendelse: AnnullerUtenlandsoppholdHendelse, meta: DefaultHendelseMetadata)
    fun hentForSakId(
        sakId: UUID,
        sessionContext: SessionContext = defaultSessionContext(),
    ): UtenlandsoppholdHendelser
    fun defaultSessionContext(): SessionContext
}
