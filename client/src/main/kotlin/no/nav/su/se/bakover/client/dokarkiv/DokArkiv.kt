package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.Either
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.client.ClientError

interface DokArkiv {
    fun opprettJournalpost(nySøknad: NySøknad, pdf: ByteArray): Either<ClientError, String>
}
