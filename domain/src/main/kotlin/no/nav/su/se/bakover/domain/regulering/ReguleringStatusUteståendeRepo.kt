package no.nav.su.se.bakover.domain.regulering

import java.util.UUID

interface ReguleringStatusUteståendeRepo {

    fun hent(): List<ProdusertReguleringStatus>
    fun hentPågående(): List<ProdusertReguleringStatus>

    fun lagreOppstartet(): UUID
    fun lagreProdusert(idPågående: UUID, reguleringStatus: ReguleringStatus)
    fun lagreFeilet(idPågående: UUID)
}
