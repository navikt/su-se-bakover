package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.tilInnvilgetForMåned
import no.nav.su.se.bakover.vedtak.domain.Vedtak
import java.util.UUID

class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo,
) : VedtakService {

    override fun lagre(vedtak: Vedtak) {
        return vedtakRepo.lagre(vedtak)
    }

    override fun lagreITransaksjon(vedtak: Vedtak, tx: TransactionContext) {
        return vedtakRepo.lagreITransaksjon(vedtak, tx)
    }

    override fun hentForVedtakId(vedtakId: UUID): Vedtak? {
        return vedtakRepo.hentVedtakForId(vedtakId)
    }

    override fun hentForRevurderingId(revurderingId: UUID): Vedtak? {
        return vedtakRepo.hentForRevurderingId(revurderingId)
    }

    override fun hentJournalpostId(vedtakId: UUID): JournalpostId? {
        return vedtakRepo.hentJournalpostId(vedtakId)
    }

    override fun hentInnvilgetFnrForMåned(måned: Måned): InnvilgetForMåned {
        return vedtakRepo.hentForMåned(måned).tilInnvilgetForMåned(måned)
    }

    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes? {
        return vedtakRepo.hentForUtbetaling(utbetalingId)
    }
}
