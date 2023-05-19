package no.nav.su.se.bakover.test.vedtak

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import java.util.UUID

fun forenkletVedtakSøknadsbehandling(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
): Vedtaksammendrag {
    return forenkletVedtak(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
        saksnummer = saksnummer,
        sakId = sakId,
    )
}

fun forenkletVedtakOpphør(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
): Vedtaksammendrag {
    return forenkletVedtak(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = Vedtakstype.REVURDERING_OPPHØR,
        saksnummer = saksnummer,
        sakId = sakId,
    )
}

fun forenkletVedtakInnvilgetRevurdering(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
): Vedtaksammendrag {
    return forenkletVedtak(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
        saksnummer = saksnummer,
        sakId = sakId,
    )
}

fun forenkletVedtak(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
    vedtakstype: Vedtakstype,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
): Vedtaksammendrag {
    return Vedtaksammendrag(
        opprettet = opprettet,
        periode = periode,
        fødselsnummer = fødselsnummer,
        vedtakstype = vedtakstype,
        saksnummer = saksnummer,
        sakId = sakId,
    )
}

fun List<Vedtak>.toVedtaksammendrag(): List<Vedtaksammendrag> {
    return this.mapNotNull {
        when (it) {
            is Klagevedtak -> null
            is Stønadsvedtak -> it.toVedtaksammendrag()
        }
    }
}

fun Stønadsvedtak.toVedtaksammendrag(): Vedtaksammendrag? {
    return when (this) {
        is VedtakAvslagBeregning,
        is VedtakAvslagVilkår,
        is VedtakGjenopptakAvYtelse,
        is VedtakStansAvYtelse,
        is VedtakInnvilgetRegulering,
        -> null

        is VedtakInnvilgetRevurdering -> toVedtaksammendrag(Vedtakstype.REVURDERING_INNVILGELSE)
        is VedtakInnvilgetSøknadsbehandling -> toVedtaksammendrag(Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE)
        is Opphørsvedtak -> toVedtaksammendrag(Vedtakstype.REVURDERING_OPPHØR)
    }
}

private fun VedtakSomKanRevurderes.toVedtaksammendrag(
    vedtakstype: Vedtakstype,
): Vedtaksammendrag {
    return Vedtaksammendrag(
        opprettet = this.opprettet,
        periode = this.periode,
        fødselsnummer = this.fnr,
        vedtakstype = vedtakstype,
        saksnummer = this.saksnummer,
        sakId = this.sakId,
    )
}
