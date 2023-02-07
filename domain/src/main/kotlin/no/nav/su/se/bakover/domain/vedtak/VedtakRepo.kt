package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface VedtakRepo {
    fun hentVedtakForId(vedtakId: UUID): Vedtak?
    fun hentForRevurderingId(revurderingId: UUID): Vedtak?
    fun hentForSakId(sakId: UUID): List<Vedtak>
    fun hentForMåned(måned: Måned): List<ForenkletVedtak>
    fun lagre(vedtak: Vedtak)
    fun lagreITransaksjon(vedtak: Vedtak, sessionContext: TransactionContext)
    fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes?
    fun hentAlle(): List<Vedtak>
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?
}
