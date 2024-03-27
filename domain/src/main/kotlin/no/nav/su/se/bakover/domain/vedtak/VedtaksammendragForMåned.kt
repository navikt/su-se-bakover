package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import java.util.UUID

data class VedtaksammendragForMåned(
    val opprettet: Tidspunkt,
    val måned: Måned,
    val fødselsnummer: Fnr,
    val vedtakstype: Vedtakstype,
    val sakId: UUID,
    val saksnummer: Saksnummer,
) {
    fun erOpphørt(): Boolean = vedtakstype == Vedtakstype.REVURDERING_OPPHØR
    fun erInnvilget(): Boolean =
        vedtakstype == Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE || vedtakstype == Vedtakstype.REVURDERING_INNVILGELSE
}
