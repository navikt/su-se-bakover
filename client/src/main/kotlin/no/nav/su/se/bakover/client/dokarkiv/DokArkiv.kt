package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnhold

interface DokArkiv {
    fun opprettJournalpost(
        søknadInnhold: SøknadInnhold,
        person: Person,
        pdf: ByteArray,
        sakId: String
    ): Either<ClientError, String>
}
