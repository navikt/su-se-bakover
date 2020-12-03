package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot

interface VedtakssnapshotRepo {
    fun opprettVedtakssnapshot(vedtakssnapshot: Vedtakssnapshot)
}
