package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.person.PdlData
import no.nav.su.se.bakover.domain.SøknadInnhold

interface DokArkiv {
    fun opprettJournalpost(
        søknadInnhold: SøknadInnhold,
        person: PdlData,
        pdf: ByteArray,
        sakId: String
    ): Either<ClientError, String>
}
