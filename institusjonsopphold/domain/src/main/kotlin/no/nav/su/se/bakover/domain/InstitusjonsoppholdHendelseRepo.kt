package no.nav.su.se.bakover.domain

import java.util.UUID

interface InstitusjonsoppholdHendelseRepo {
    fun lagre(hendelse: InstitusjonsoppholdHendelse)
    fun hentForSak(sakId: UUID): InstitusjonsoppholdHendelserPåSak?
    fun hentTidligereInstHendelserForOpphold(sakId: UUID, oppholdId: OppholdId): List<InstitusjonsoppholdHendelse>
}
