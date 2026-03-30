package no.nav.su.se.bakover.domain.regulering

import java.util.UUID

interface ReguleringKjøringRepo {
    fun lagre(oppsummering: ReguleringKjøring)
    fun hent(id: UUID): ReguleringKjøring?
}
