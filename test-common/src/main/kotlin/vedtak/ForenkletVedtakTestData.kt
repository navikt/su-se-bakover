package no.nav.su.se.bakover.test.vedtak

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
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
        is Avslagsvedtak.AvslagBeregning,
        is Avslagsvedtak.AvslagVilkår,
        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse,
        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse,
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering,
        -> null

        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> toVedtaksammendrag(Vedtakstype.REVURDERING_INNVILGELSE)
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> toVedtaksammendrag(Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE)
        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> toVedtaksammendrag(Vedtakstype.REVURDERING_OPPHØR)
    }
}

private fun VedtakSomKanRevurderes.EndringIYtelse.toVedtaksammendrag(
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
