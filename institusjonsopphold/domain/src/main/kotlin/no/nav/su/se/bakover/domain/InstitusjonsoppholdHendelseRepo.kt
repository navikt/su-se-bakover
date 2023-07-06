package no.nav.su.se.bakover.domain

import java.util.UUID

interface InstitusjonsoppholdHendelseRepo {
    fun lagre(hendelse: InstitusjonsoppholdHendelse.KnyttetTilSak)
    fun hent(id: UUID): InstitusjonsoppholdHendelse.KnyttetTilSak?
    fun hentHendelserUtenOppgaveId(): List<InstitusjonsoppholdHendelse.KnyttetTilSak>
}
