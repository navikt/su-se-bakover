package no.nav.su.se.bakover.service.journalføring

import arrow.core.Either
import arrow.core.left
import dokument.domain.Dokument
import dokument.domain.Dokumentdistribusjon
import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.brev.KunneIkkeBestilleBrevForDokument
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.backoff.Failures
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.tilResultat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.minimumPdfAzeroPadded
import no.nav.su.se.bakover.test.sakinfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.Logger
import java.util.UUID

internal class JournalføringOgDistribueringsResultatTest {

    @Test
    fun `logger warn ved første feilede distribusjon`() {
        val log = mock<Logger>()

        feil(KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev)
            .tilResultat(dokumentdistribusjon(tidligereFeil = 0), log)
            .shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()

        verify(log).warn(argThat<String> { contains("Antall feilede forsøk: 1") })
        verify(log, never()).error(any<String>())
        verify(log, never()).error(any<String>(), anyOrNull<Throwable>())
    }

    @Test
    fun `logger error når distribusjonen har feilet to ganger på rad`() {
        val log = mock<Logger>()

        feil(KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev)
            .tilResultat(dokumentdistribusjon(tidligereFeil = 1), log)
            .shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()

        verify(log).error(argThat<String> { contains("Antall feilede forsøk: 2") })
        verify(log, never()).warn(any<String>())
    }

    @Test
    fun `logger error med stacktrace når dokumentet må journalføres først`() {
        val log = mock<Logger>()

        feil(KunneIkkeBestilleBrevForDokument.MåJournalføresFørst)
            .tilResultat(dokumentdistribusjon(tidligereFeil = 0), log)
            .shouldBeInstanceOf<JournalføringOgDistribueringsResultat.Feil>()

        verify(log).error(any<String>(), any<Throwable>())
        verify(log, never()).warn(any<String>())
    }

    private fun feil(feil: KunneIkkeBestilleBrevForDokument): Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon> =
        feil.left()

    private fun dokumentdistribusjon(tidligereFeil: Long): Dokumentdistribusjon = Dokumentdistribusjon(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        dokument = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = minimumPdfAzeroPadded(),
            generertDokumentJson = "{}",
            metadata = Dokument.Metadata(sakId = sakinfo.sakId),
            distribueringsadresse = null,
        ),
        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(
            journalpostId = JournalpostId("123"),
            distribusjonFailures = Failures(count = tidligereFeil, last = if (tidligereFeil > 0) fixedTidspunkt else null),
        ),
    )
}
