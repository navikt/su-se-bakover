package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.LocalDate
import java.util.UUID

interface VedtakService {
    fun lagre(vedtak: Vedtak)
    fun lagre(vedtak: Vedtak, sessionContext: TransactionContext)
    fun hentForVedtakId(vedtakId: UUID): Vedtak?
    fun hentForRevurderingId(revurderingId: UUID): Vedtak?
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?
    fun hentAktiveFnr(fomDato: LocalDate): List<Fnr>
    fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes?
}
