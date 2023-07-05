package no.nav.su.se.bakover.domain

interface InstitusjonsoppholdHendelseRepo {
    fun lagre(hendelse: InstitusjonsoppholdHendelse)
    fun hentHendelserUtenOppgaveId(): List<InstitusjonsoppholdHendelse>
}
