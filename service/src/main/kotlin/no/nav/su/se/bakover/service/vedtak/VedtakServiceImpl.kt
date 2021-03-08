package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.LocalDate

class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo
) : VedtakService {
    override fun hentAktive(fomDato: LocalDate) : List<Vedtak.InnvilgetStønad> {
        return vedtakRepo.hentAktive(fomDato)
    }
}
