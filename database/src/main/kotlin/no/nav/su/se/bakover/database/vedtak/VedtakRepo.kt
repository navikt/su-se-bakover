package no.nav.su.se.bakover.database.vedtak

import no.nav.su.se.bakover.domain.vedtak.Vedtak

interface VedtakRepo {
    fun opprettVedtak(vedtak: Vedtak)
}
