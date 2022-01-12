package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.time.LocalDate
import java.util.UUID

interface VedtakRepo {
    fun hentForVedtakId(vedtakId: UUID): Vedtak?
    fun hentForSakId(sakId: UUID): List<Vedtak>
    fun hentAktive(dato: LocalDate): List<Vedtak.EndringIYtelse>
    fun lagre(vedtak: Vedtak)
    fun lagre(vedtak: Vedtak, sessionContext: TransactionContext)
    fun hentForUtbetaling(utbetalingId: UUID30): Vedtak?
    fun hentAlle(): List<Vedtak>
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?
}
