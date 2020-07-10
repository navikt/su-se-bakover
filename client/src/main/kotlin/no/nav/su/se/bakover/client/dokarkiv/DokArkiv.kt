package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.SøknadInnhold

interface DokArkiv {
    fun opprettJournalpost(
        nySøknad: SøknadInnhold,
        pdf: ByteArray,
        sakId: String
    ): Either<ClientError, String>
}
