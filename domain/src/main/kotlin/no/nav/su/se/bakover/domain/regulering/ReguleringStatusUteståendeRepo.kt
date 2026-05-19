package no.nav.su.se.bakover.domain.regulering

import java.util.UUID

interface ReguleringStatusUteståendeRepo {

    fun hent(): List<ReguleringStatus>
    fun hentPågående(): List<ReguleringStatus>

    fun lagreOppstartet(): UUID
    fun lagreProdusert(idPågående: UUID, reguleringStatus: ReguleringStatus)
    fun lagreFeilet(idPågående: UUID)
}
