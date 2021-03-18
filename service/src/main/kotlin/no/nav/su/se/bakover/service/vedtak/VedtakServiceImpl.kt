package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

class VedtakServiceImpl(
    private val vedtakRepo: VedtakRepo
) : VedtakService {
    override fun hentAktiveFnr(fomDato: LocalDate): List<Fnr> {
        return vedtakRepo.hentAktive(fomDato).map {
            it.behandling.fnr
        }.sortedWith(compareBy(Fnr::toString)).distinct()
    }
}
