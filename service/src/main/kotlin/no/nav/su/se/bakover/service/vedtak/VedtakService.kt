package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.LocalDate

interface VedtakService {
    fun hentAktive(fomDato: LocalDate): List<Vedtak.InnvilgetStønad>
}
