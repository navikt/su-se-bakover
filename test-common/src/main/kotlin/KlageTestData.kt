@file:Suppress("unused")

package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import java.time.LocalDate
import java.util.UUID

fun opprettetKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021)
): OpprettetKlage {
    return OpprettetKlage.create(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
    )
}

/**
 * @return [VilkårsvurdertKlage.Påbegynt]
 * @throws RuntimeException dersom alle ingen av feltene: (vedtakId, innenforFristen, klagesDetPåKonkreteElementerIVedtaket, erUnderskrevet, begrunnelse) er null.
 *
 */
fun vilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID? = null,
    innenforFristen: Boolean? = null,
    klagesDetPåKonkreteElementerIVedtaket: Boolean? = null,
    erUnderskrevet: Boolean? = null,
    begrunnelse: String? = null,
): VilkårsvurdertKlage.Påbegynt {
    return OpprettetKlage.create(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt
    ).vilkårsvurder(
        saksbehandler = saksbehandler,
        vilkårsvurderinger = VilkårsvurderingerTilKlage.create(
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
    } as VilkårsvurdertKlage.Påbegynt
}

fun utfyltVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: Boolean = true,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: Boolean = true,
    begrunnelse: String = "begrunnelse",
): VilkårsvurdertKlage.Utfylt {
    return OpprettetKlage.create(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt
    ).vilkårsvurder(
        saksbehandler = saksbehandler,
        vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
            vedtakId = vedtakId,
            innenforFristen = innenforFristen,
            klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = erUnderskrevet,
            begrunnelse = begrunnelse,
        ),
    )
}
