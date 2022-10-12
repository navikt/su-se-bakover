package no.nav.su.se.bakover.utenlandsopphold.domain

interface RegistrerUtenlandsoppholdRepo {
    fun lagre(hendelse: RegistrerUtenlandsoppholdHendelse)
}
