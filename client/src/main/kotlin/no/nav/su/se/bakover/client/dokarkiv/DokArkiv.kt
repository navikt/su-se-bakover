package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.Person

interface DokArkiv {
    fun <T> opprettJournalpost(
        dokumentInnhold: T,
        person: Person,
        pdf: ByteArray,
        sakId: String
    ): Either<ClientError, String>
}
