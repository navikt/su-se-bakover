package no.nav.su.se.bakover.test.vedtak

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.vedtak.ForenkletVedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer

fun forenkletVedtakSøknadsbehandling(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
): ForenkletVedtak {
    return forenkletVedtak(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = Vedtakstype.SØKNADSBEHANDLING,
    )
}

fun forenkletVedtakOpphør(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
): ForenkletVedtak {
    return forenkletVedtak(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = Vedtakstype.REVURDERING_OPPHØR,
    )
}

fun forenkletVedtakInnvilgetRevurdering(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
): ForenkletVedtak {
    return forenkletVedtak(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
    )
}

fun forenkletVedtak(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
    vedtakstype: Vedtakstype,
): ForenkletVedtak {
    return ForenkletVedtak(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = vedtakstype,
    )
}
