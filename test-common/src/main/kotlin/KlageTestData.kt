@file:Suppress("unused")

package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
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
fun påbegyntVilkårsvurdertKlage(
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
    return opprettetKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
    ).vilkårsvurder(
        saksbehandler = saksbehandler,
        vilkårsvurderinger = VilkårsvurderingerTilKlage.create(
            vedtakId = vedtakId,
            innenforFristen = innenforFristen,
            klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = erUnderskrevet,
            begrunnelse = begrunnelse,
        ) as VilkårsvurderingerTilKlage.Påbegynt,
    )
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
    return opprettetKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
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

fun bekreftetVilkårsvurdertKlage(
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
): VilkårsvurdertKlage.Bekreftet {
    return utfyltVilkårsvurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
    ).bekreftVilkårsvurderinger(
        saksbehandler = saksbehandler,
    ).orNull()!!
}

fun påbegyntVurdertKlage(
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
    fritekstTilBrev: String? = null,
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering? = null,
): VurdertKlage.Påbegynt {
    assert(vedtaksvurdering == null || vedtaksvurdering is VurderingerTilKlage.Vedtaksvurdering.Påbegynt)
    return bekreftetVilkårsvurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
    ).vurder(
        saksbehandler = saksbehandler,
        vurderinger = VurderingerTilKlage.create(
            fritekstTilBrev = fritekstTilBrev,
            vedtaksvurdering = vedtaksvurdering,
        ) as VurderingerTilKlage.Påbegynt,
    )
}

fun utfyltVurdertKlage(
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
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
): VurdertKlage.Utfylt {
    return bekreftetVilkårsvurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
    ).vurder(
        saksbehandler = saksbehandler,
        vurderinger = VurderingerTilKlage.create(
            fritekstTilBrev = fritekstTilBrev,
            vedtaksvurdering = vedtaksvurdering,
        ) as VurderingerTilKlage.Utfylt,
    )
}

fun bekreftetVurdertKlage(
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
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
): VurdertKlage.Bekreftet {
    return utfyltVurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
    ).bekreftVurderinger(
        saksbehandler = saksbehandler,
    ).orNull()!!
}

fun klageTilAttestering(
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
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
): KlageTilAttestering {
    return bekreftetVurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
    ).sendTilAttestering(
        saksbehandler = saksbehandler,
    ).orNull()!!
}

fun underkjentKlage(
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
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("attestant"),
    attesteringsgrunn: Attestering.Underkjent.Grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
    attesteringskommentar: String = "attesteringskommentar",
): VurdertKlage.Bekreftet {
    return klageTilAttestering(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
    ).underkjenn(
        underkjentAttestering =
        Attestering.Underkjent(
            attestant = attestant,
            opprettet = opprettet,
            grunn = attesteringsgrunn,
            kommentar = attesteringskommentar,
        ),
    ).orNull()!!
}

fun iverksattKlage(
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
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("attestant"),
): IverksattKlage {
    return klageTilAttestering(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
    ).iverksett(
        iverksattAttestering = Attestering.Iverksatt(
            attestant = attestant,
            opprettet = opprettet,

        ),
    ).orNull()!!
}
