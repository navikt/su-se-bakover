package no.nav.su.se.bakover.test.vedtak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import vedtak.domain.Stønadsvedtak
import vedtak.domain.Vedtak
import vedtak.domain.VedtakSomKanRevurderes
import java.util.UUID

fun forenkletVedtakSøknadsbehandling(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fødselsnummer: Fnr = Fnr.generer(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    sakId: UUID = UUID.randomUUID(),
): VedtaksammendragForSak {
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
): VedtaksammendragForSak {
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
): VedtaksammendragForSak {
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
): VedtaksammendragForSak {
    return VedtaksammendragForSak(
        fødselsnummer = fødselsnummer,
        saksnummer = saksnummer,
        sakId = sakId,
        vedtak = listOf(
            VedtaksammendragForSak.Vedtak(
                opprettet = opprettet,
                periode = periode,
                vedtakstype = vedtakstype,
            ),
        ),
    )
}

fun vedtaksammendragForSak(
    fødselsnummer: Fnr = Fnr.generer(),
    sakId: UUID = UUID.randomUUID(),
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    vararg listeAvVedtak: VedtaksammendragForSak.Vedtak = arrayOf(vedtaksammendragForSakVedtak()),
): VedtaksammendragForSak {
    return VedtaksammendragForSak(
        fødselsnummer = fødselsnummer,
        saksnummer = saksnummer,
        sakId = sakId,
        vedtak = listeAvVedtak.toList(),
    )
}

fun vedtaksammendragForSakVedtak(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    vedtakstype: Vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
): VedtaksammendragForSak.Vedtak {
    return VedtaksammendragForSak.Vedtak(
        opprettet = opprettet,
        periode = periode,
        vedtakstype = vedtakstype,
    )
}

fun List<Vedtak>.toVedtaksammendrag(): List<VedtaksammendragForSak> {
    return this.mapNotNull {
        when (it) {
            is Klagevedtak -> null
            is Stønadsvedtak -> it.toVedtaksammendrag()
            else -> throw IllegalStateException("Vedtak er av ukjent type - ${it::class.simpleName}")
        }
    }
}

// TODO jah: Kan løses ved en abstract function på Stønadsvedtak
fun Stønadsvedtak.toVedtaksammendrag(): VedtaksammendragForSak? {
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
        else -> throw IllegalStateException("Vedtak er av ukjent type - ${this::class.simpleName}")
    }
}

private fun VedtakSomKanRevurderes.toVedtaksammendrag(
    vedtakstype: Vedtakstype,
): VedtaksammendragForSak {
    return VedtaksammendragForSak(
        fødselsnummer = this.fnr,
        saksnummer = this.saksnummer,
        sakId = this.sakId,
        vedtak = listOf(
            VedtaksammendragForSak.Vedtak(
                opprettet = this.opprettet,
                periode = this.periode,
                vedtakstype = vedtakstype,
            ),
        ),
    )
}
