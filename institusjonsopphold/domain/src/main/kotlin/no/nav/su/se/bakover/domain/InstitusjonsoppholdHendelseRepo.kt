package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

interface InstitusjonsoppholdHendelseRepo {
    fun lagre(hendelse: InstitusjonsoppholdHendelse)
    fun hentForSak(sakId: UUID): InstitusjonsoppholdHendelserPåSak?
    fun hentSisteVersjonFor(sakId: UUID): Hendelsesversjon?
    fun hentTidligereOpphold(oppholdId: OppholdId): List<InstitusjonsoppholdHendelse>
}
