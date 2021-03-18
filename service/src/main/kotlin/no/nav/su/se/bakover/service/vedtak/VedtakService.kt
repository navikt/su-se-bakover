package no.nav.su.se.bakover.service.vedtak

import no.nav.su.se.bakover.domain.Fnr
import java.time.LocalDate

interface VedtakService {
    fun hentAktiveFnr(fomDato: LocalDate): List<Fnr>
}
