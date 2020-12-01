package no.nav.su.se.bakover.service.vedtak.snapshot

import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotRepo
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot

class OpprettVedtakssnapshotService(
    private val vedtakssnapshotRepo: VedtakssnapshotRepo
) {
    fun opprettVedtak(vedtakssnapshot: Vedtakssnapshot) {
        vedtakssnapshotRepo.opprettVedtakssnapshot(vedtakssnapshot)
    }
}
