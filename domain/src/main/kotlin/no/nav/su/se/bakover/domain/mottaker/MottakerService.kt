package no.nav.su.se.bakover.domain.mottaker

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

interface MottakerRepo {
    fun hentMottaker(dokumentId: UUID, sessionContext: SessionContext? = null): Mottaker?
    fun lagreMottaker(mottaker: Mottaker, dokumentId: UUID)
    fun oppdaterMottaker(mottaker: Mottaker, dokumentId: UUID)
    fun slettMottaker(mottakerId: UUID, dokumentId: UUID)
}

class MottakerService

data class Mottaker(
    val id: UUID = UUID.randomUUID(),
    val navn: String,
    val foedselsnummer: Fnr,
    val orgnummer: String? = null,
    val adresse: Adresse,
    val dokumentId: UUID,
)

data class Adresse(
    val adresselinje1: String,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String,
    val poststed: String,
    val landkode: String? = null,
    val land: String? = null,
)
