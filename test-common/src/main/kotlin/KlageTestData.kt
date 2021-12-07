@file:Suppress("unused")

package no.nav.su.se.bakover.test

import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
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
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID

fun opprettetKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, OpprettetKlage> {
    assert(sakMedVedtak.vedtakListe.isNotEmpty())
    val klage = OpprettetKlage.create(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
    )
    return Pair(
        sakMedVedtak.copy(klager = sakMedVedtak.klager + klage),
        klage,
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
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID? = null,
    innenforFristen: VilkårsvurderingerTilKlage.Svarord? = null,
    klagesDetPåKonkreteElementerIVedtaket: Boolean? = null,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord? = null,
    begrunnelse: String? = null,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, VilkårsvurdertKlage.Påbegynt> {
    return opprettetKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        sakMedVedtak = sakMedVedtak,
    ).let { (sak, klage) ->
        val vilkårsvurdertKlage = klage.vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkårsvurderinger = VilkårsvurderingerTilKlage.create(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                begrunnelse = begrunnelse,
            ) as VilkårsvurderingerTilKlage.Påbegynt,
        )
        Pair(
            sak.copy(klager = sak.klager.filterNot { it.id == vilkårsvurdertKlage.id } + vilkårsvurdertKlage),
            vilkårsvurdertKlage,
        )
    }
}

fun utfyltVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, VilkårsvurdertKlage.Utfylt> {
    return opprettetKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.vilkårsvurder(
            saksbehandler = saksbehandler,
            vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                vedtakId = vedtakId,
                innenforFristen = innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet = erUnderskrevet,
                begrunnelse = begrunnelse,
            ),
        )
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun bekreftetVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, VilkårsvurdertKlage.Bekreftet> {
    return utfyltVilkårsvurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.bekreftVilkårsvurderinger(
            saksbehandler = saksbehandler,
        ).orNull()!!
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun påbegyntVurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String? = null,
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering? = null,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, VurdertKlage.Påbegynt> {
    assert(vedtaksvurdering == null || vedtaksvurdering is VurderingerTilKlage.Vedtaksvurdering.Påbegynt)
    return bekreftetVilkårsvurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.vurder(
            saksbehandler = saksbehandler,
            vurderinger = VurderingerTilKlage.create(
                fritekstTilBrev = fritekstTilBrev,
                vedtaksvurdering = vedtaksvurdering,
            ) as VurderingerTilKlage.Påbegynt,
        )
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun utfyltVurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, VurdertKlage.Utfylt> {
    return bekreftetVilkårsvurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.vurder(
            saksbehandler = saksbehandler,
            vurderinger = VurderingerTilKlage.create(
                fritekstTilBrev = fritekstTilBrev,
                vedtaksvurdering = vedtaksvurdering,
            ) as VurderingerTilKlage.Utfylt,
        )
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun bekreftetVurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, VurdertKlage.Bekreftet> {
    return utfyltVurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.bekreftVurderinger(
            saksbehandler = saksbehandler,
        ).orNull()!!
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun klageTilAttestering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, KlageTilAttestering> {
    return bekreftetVurdertKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveId = OppgaveId("klageOppgaveId"),
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.sendTilAttestering(
            opprettOppgave = { oppgaveIdTilAttestering.right() },
            saksbehandler = saksbehandler,
        ).orNull()!!
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun underkjentKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    underkjentKlageOppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("attestant"),
    attesteringsgrunn: Attestering.Underkjent.Grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
    attesteringskommentar: String = "attesteringskommentar",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, VurdertKlage.Bekreftet> {
    return klageTilAttestering(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveIdTilAttestering = OppgaveId("klageTilAttesteringOppgaveId"),
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = attestant,
                opprettet = opprettet,
                grunn = attesteringsgrunn,
                kommentar = attesteringskommentar,
            ),
            opprettOppgave = { underkjentKlageOppgaveId.right() },
        ).orNull()!!
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun underkjentKlageTilAttestering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("underkjentKlageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, KlageTilAttestering> {
    return underkjentKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        underkjentKlageOppgaveId = OppgaveId("underkjentKlageOppgaveId"),
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.sendTilAttestering(
            saksbehandler = saksbehandler,
            opprettOppgave = { oppgaveIdTilAttestering.right() },
        ).orNull()!!
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun iverksattKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).orNull()!!,
    ).orNull()!!,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("attestant"),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, IverksattKlage> {
    return klageTilAttestering(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        journalpostId = journalpostId,
        oppgaveIdTilAttestering = oppgaveIdTilAttestering,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
        vedtakId = vedtakId,
        innenforFristen = innenforFristen,
        klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = erUnderskrevet,
        begrunnelse = begrunnelse,
        fritekstTilBrev = fritekstTilBrev,
        vedtaksvurdering = vedtaksvurdering,
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.iverksett(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = attestant,
                opprettet = opprettet,

            ),
        ).orNull()!!
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}
