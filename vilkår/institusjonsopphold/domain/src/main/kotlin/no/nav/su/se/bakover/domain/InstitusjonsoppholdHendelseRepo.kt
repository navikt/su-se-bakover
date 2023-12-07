package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import java.util.UUID

interface InstitusjonsoppholdHendelseRepo {
    fun lagre(hendelse: InstitusjonsoppholdHendelse, meta: DefaultHendelseMetadata)
    fun hentForSak(sakId: UUID): InstitusjonsoppholdHendelserPåSak?
    fun hentTidligereInstHendelserForOpphold(sakId: UUID, oppholdId: OppholdId): List<InstitusjonsoppholdHendelse>
}
