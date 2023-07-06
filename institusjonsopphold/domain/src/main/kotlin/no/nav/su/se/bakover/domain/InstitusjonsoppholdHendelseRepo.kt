package no.nav.su.se.bakover.domain

import java.util.UUID

interface InstitusjonsoppholdHendelseRepo {
    fun lagre(hendelse: InstitusjonsoppholdHendelse)
    fun hent(id: UUID): InstitusjonsoppholdHendelse.KnyttetTilSak?
    fun hentHendelserUtenOppgaveId(): List<InstitusjonsoppholdHendelse>
}
