package no.nav.su.se.bakover.utenlandsopphold.domain

import java.util.UUID

interface RegistrertUtenlandsoppholdRepo {
    fun lagre(sakId: UUID, registrertUtenlandsopphold: RegistrertUtenlandsopphold)
}
