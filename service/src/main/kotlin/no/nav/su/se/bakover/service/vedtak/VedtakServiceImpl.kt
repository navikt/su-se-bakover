package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
) : VedtakService {

    override fun lagre(vedtak: Vedtak) {
        return vedtakRepo.lagre(vedtak)
    }

    override fun lagreITransaksjon(vedtak: Vedtak, sessionContext: TransactionContext) {
        return vedtakRepo.lagreITransaksjon(vedtak, sessionContext)
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

    override fun hentAktiveFnr(fomDato: LocalDate): List<Fnr> {
        return vedtakRepo.hentAktive(fomDato).map {
            it.behandling.fnr
        }.sortedWith(compareBy(Fnr::toString)).distinct()
    }

    override fun hentForUtbetaling(utbetalingId: UUID30): VedtakSomKanRevurderes? {
        return vedtakRepo.hentForUtbetaling(utbetalingId)
    }
}
