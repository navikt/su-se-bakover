package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate
import java.util.UUID

interface VedtakRepo {
    fun hentVedtakForId(vedtakId: UUID): Vedtak?
    fun hentForRevurderingId(revurderingId: UUID): Vedtak?
    fun hentForMåned(måned: Måned): List<Vedtaksammendrag>
    fun hentForFødselsnumreOgFraOgMedMåned(fødselsnumre: List<Fnr>, fraOgMed: Måned): List<Vedtaksammendrag>
    fun lagre(vedtak: Vedtak)
    fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext)
    fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes?
    fun hentJournalpostId(vedtakId: UUID): JournalpostId?
    fun hentSøknadsbehandlingsvedtakFraOgMed(
        fraOgMed: LocalDate,
    ): List<UUID>
}
