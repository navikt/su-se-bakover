@file:Suppress("unused")

package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import java.util.UUID

fun opprettetKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
): OpprettetKlage {
    return OpprettetKlage.create(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
    )
}

fun påbegyntVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    vedtakId: UUID? = null,
    innenforFristen: Boolean? = null,
    klagesDetPåKonkreteElementerIVedtaket: Boolean? = null,
    erUnderskrevet: Boolean? = null,
    begrunnelse: String? = null,
): VilkårsvurdertKlage {
    return OpprettetKlage.create(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
    ).vilkårsvurder(
        saksbehandler = saksbehandler,
        vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt(
            vedtakId = vedtakId,
            innenforFristen = innenforFristen,
            klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = erUnderskrevet,
            begrunnelse = begrunnelse,
        ),
    ).orNull()!!.also {
        assert(it is VilkårsvurdertKlage.Påbegynt) {
            "Dersom ingen av de vilkårsvurderte feltene er null, vil vi få en VilkårsvurdertKlage.Utfylt istedet for Påbegynt."
        }
    }
}

fun ferdigVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: Boolean = true,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: Boolean = true,
    begrunnelse: String = "begrunnelse",
): VilkårsvurdertKlage {
    return OpprettetKlage.create(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
    ).vilkårsvurder(
        saksbehandler = saksbehandler,
        vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
            vedtakId = vedtakId,
            innenforFristen = innenforFristen,
            klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = erUnderskrevet,
            begrunnelse = begrunnelse,
        ),
    ).orNull()!!
}
