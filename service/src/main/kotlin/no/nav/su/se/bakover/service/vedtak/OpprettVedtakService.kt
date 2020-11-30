package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.Vedtak

class OpprettVedtakService(
    private val vedtakRepo: VedtakRepo
) {
    fun opprettVedtak(vedtak: Vedtak) {
        vedtakRepo.opprettVedtak(vedtak)
    }
}
