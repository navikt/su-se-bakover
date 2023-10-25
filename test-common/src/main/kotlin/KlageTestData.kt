@file:Suppress("unused")

package no.nav.su.se.bakover.test

import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.nyKlage
import no.nav.su.se.bakover.domain.sak.oppdaterKlage
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

val oppgaveIdKlage = OppgaveId("oppgaveIdKlage")
fun opprettetKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, OpprettetKlage> {
    require(sakMedVedtak.vedtakListe.isNotEmpty())
    require(opprettet.toLocalDate(ZoneId.of("UTC")) >= datoKlageMottatt)
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
    val klage = OpprettetKlage(
        id = id,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = sakMedVedtak.saksnummer,
        fnr = sakMedVedtak.fnr,
        journalpostId = journalpostId,
        oppgaveId = oppgaveId,
        saksbehandler = saksbehandler,
        datoKlageMottatt = datoKlageMottatt,
    )
    return Pair(
        sakMedVedtak.nyKlage(klage),
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
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID? = null,
    innenforFristen: VilkårsvurderingerTilKlage.Svarord? = null,
    klagesDetPåKonkreteElementerIVedtaket: Boolean? = null,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord? = null,
    begrunnelse: String? = null,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
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
        ).getOrFail()

        if (vilkårsvurdertKlage !is VilkårsvurdertKlage.Påbegynt) throw IllegalStateException("Forventet en Vilkårsvurdert.Påbegynt, men fikk ${vilkårsvurdertKlage::class} ved oppretting av test data")

        Pair(
            sak.oppdaterKlage(vilkårsvurdertKlage),
            vilkårsvurdertKlage,
        )
    }
}

fun utfyltVilkårsvurdertKlageTilVurdering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, VilkårsvurdertKlage.Utfylt.TilVurdering> {
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
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
        ).getOrFail()

        if (klage !is VilkårsvurdertKlage.Utfylt.TilVurdering) throw IllegalStateException("Forventet en Vilkårsvurdert.Utfylt(TilVurdering), men fikk ${klage::class} ved oppretting av test data")

        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun utfyltAvvistVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, VilkårsvurdertKlage.Utfylt.Avvist> {
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
            saksbehandler,
            VilkårsvurderingerTilKlage.create(
                vedtakId,
                innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket,
                erUnderskrevet,
                begrunnelse,
            ),
        ).getOrFail()

        if (klage !is VilkårsvurdertKlage.Utfylt.Avvist) throw IllegalStateException("Forventet en Vilkårsvurdert.Utfylt(Avvist), men fikk ${klage::class} ved oppretting av test-data")

        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun bekreftetVilkårsvurdertKlageTilVurdering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, VilkårsvurdertKlage.Bekreftet.TilVurdering> {
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
    return utfyltVilkårsvurdertKlageTilVurdering(
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
        ).getOrFail()

        if (klage !is VilkårsvurdertKlage.Bekreftet.TilVurdering) throw IllegalStateException("Forventet en Vilkårsvurdert.Bekreftet(TilVurdering), men fikk ${klage::class} ved oppretting av test-data")

        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun bekreftetAvvistVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, VilkårsvurdertKlage.Bekreftet.Avvist> {
    return utfyltAvvistVilkårsvurdertKlage(
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
        ).getOrFail()

        if (klage !is VilkårsvurdertKlage.Bekreftet.Avvist) throw IllegalStateException("Forventet en Vilkårsvurdert.Bekreftet(Avvist), men fikk ${klage::class} ved oppretting av test-data")

        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun påbegyntVurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String? = null,
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering? = null,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, VurdertKlage.Påbegynt> {
    require(vedtaksvurdering == null || vedtaksvurdering is VurderingerTilKlage.Vedtaksvurdering.Påbegynt)
    return bekreftetVilkårsvurdertKlageTilVurdering(
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
                fritekstTilOversendelsesbrev = fritekstTilBrev,
                vedtaksvurdering = vedtaksvurdering,
            ) as VurderingerTilKlage.Påbegynt,
        )
        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun utfyltVurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    fnr: Fnr = Fnr.generer(),
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
    ).getOrFail(),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId, fnr = fnr).first,
): Pair<Sak, VurdertKlage.Utfylt> {
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
    return bekreftetVilkårsvurdertKlageTilVurdering(
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
                fritekstTilOversendelsesbrev = fritekstTilBrev,
                vedtaksvurdering = vedtaksvurdering,
            ) as VurderingerTilKlage.Utfylt,
        )
        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun bekreftetVurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
    ).getOrFail(),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, VurdertKlage.Bekreftet> {
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
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
        )
        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun avvistKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "dette er en fritekst med person opplysninger",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, AvvistKlage> {
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
    return bekreftetAvvistVilkårsvurdertKlage(
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
        val klage = it.second.leggTilFritekstTilAvvistVedtaksbrev(
            saksbehandler,
            fritekstTilBrev,
        )

        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

/**
 * Underliggende klage som ble avsluttet er [VurdertKlage.Bekreftet]
 */
fun avsluttetKlage(
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    begrunnelse: String = "Begrunnelse for avsluttet klage.",
    tidspunktAvsluttet: Tidspunkt = fixedTidspunkt,
): Pair<Sak, AvsluttetKlage> {
    return bekreftetVurdertKlage().let { (sak, bekreftetVurdertKlage) ->
        val avsluttetKlage = bekreftetVurdertKlage.avslutt(
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).getOrFail()
        Pair(sak.oppdaterKlage(avsluttetKlage), avsluttetKlage)
    }
}

fun vurdertKlageTilAttestering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
    ).getOrFail(),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, KlageTilAttestering.Vurdert> {
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
        ).getOrFail()

        if (klage !is KlageTilAttestering.Vurdert) throw IllegalStateException("Forventet en KlageTilAttestering(TilVurdering) ved opprettelse av test data. Fikk ${klage::class}")
        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun avvistKlageTilAttestering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "dette er en fritekst med person opplysninger",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, KlageTilAttestering.Avvist> {
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
    return avvistKlage(
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
        sakMedVedtak = sakMedVedtak,
        fritekstTilBrev = fritekstTilBrev,
    ).let {
        val klage = it.second.sendTilAttestering(
            saksbehandler,
        ) { oppgaveIdTilAttestering.right() }.getOrFail()

        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun underkjentKlageTilVurdering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    underkjentKlageOppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
    ).getOrFail(),
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    attesteringsgrunn: Attestering.Underkjent.Grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
    attesteringskommentar: String = "attesteringskommentar",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, VurdertKlage.Bekreftet> {
    return vurdertKlageTilAttestering(
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
        ) { underkjentKlageOppgaveId.right() }.getOrFail()
        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun underkjentAvvistKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    underkjentKlageOppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    attesteringsgrunn: Attestering.Underkjent.Grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
    attesteringskommentar: String = "attesteringskommentar",
): Pair<Sak, AvvistKlage> {
    require(sakId == sakMedVedtak.id) {
        "Saken id ${sakMedVedtak.id} var ulik sakId $sakId"
    }
    return avvistKlageTilAttestering(
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
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.underkjenn(
            underkjentAttestering = Attestering.Underkjent(
                attestant = attestant,
                opprettet = opprettet,
                grunn = attesteringsgrunn,
                kommentar = attesteringskommentar,
            ),
        ) { underkjentKlageOppgaveId.right() }.getOrFail()

        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun underkjentTilVurderingKlageTilAttestering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("underkjentKlageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
    ).getOrFail(),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
): Pair<Sak, KlageTilAttestering> {
    return underkjentKlageTilVurdering(
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
        ).getOrFail()
        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun oversendtKlage(
    clock: Clock = TikkendeKlokke(),
    klageId: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = Tidspunkt.now(clock).plus(31, ChronoUnit.DAYS),
    sakId: UUID = UUID.randomUUID(),
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "fritekstTilBrev",
    vedtaksvurdering: VurderingerTilKlage.Vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
        hjemler = Hjemler.tryCreate(listOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4)).getOrFail(),
    ).getOrFail(),
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(
        sakOgSøknad = nySakUføre(
            sakInfo = SakInfo(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = Fnr.generer(),
                type = Sakstype.UFØRE,
            ),
            clock = clock,
        ),
    ).first,
): Pair<Sak, OversendtKlage> {
    return vurdertKlageTilAttestering(
        id = klageId,
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
        val klage = it.second.oversend(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = attestant,
                opprettet = opprettet,
            ),
        ).getOrFail()
        Pair(
            it.first.oppdaterKlage(klage),
            klage,
        )
    }
}

fun iverksattAvvistKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt.plus(31, ChronoUnit.DAYS),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 15.januar(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "dette er en fritekst med person opplysninger",
    attestant: NavIdentBruker.Attestant = no.nav.su.se.bakover.test.attestant,
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget(sakId = sakId).first,
    clock: Clock = fixedClock,
): Pair<Sak, IverksattAvvistKlage> {
    return avvistKlageTilAttestering(
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
        sakMedVedtak = sakMedVedtak,
    ).let {
        val klage = it.second.iverksett(
            iverksattAttestering = Attestering.Iverksatt(
                attestant = attestant,
                opprettet = opprettet,
            ),
        ).getOrFail()

        Pair(
            it.first.oppdaterKlage(klage)
                .copy(
                    vedtakListe = it.first.vedtakListe + Klagevedtak.Avvist.fromIverksattAvvistKlage(klage, clock),
                ),
            klage,
        )
    }
}

fun createBekreftetVilkårsvurdertKlage(
    id: UUID,
    opprettet: Tidspunkt,
    sakId: UUID,
    saksnummer: Saksnummer,
    fnr: Fnr,
    journalpostId: JournalpostId,
    oppgaveId: OppgaveId,
    saksbehandler: NavIdentBruker.Saksbehandler,
    vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    attesteringer: Attesteringshistorikk,
    datoKlageMottatt: LocalDate,
    vurderinger: VurderingerTilKlage? = null,
    fritekstTilBrev: String? = null,
    klageinstanshendelser: Klageinstanshendelser = Klageinstanshendelser.empty(),
): VilkårsvurdertKlage.Bekreftet {
    return if (vilkårsvurderinger.erAvvist()) {
        VilkårsvurdertKlage.Bekreftet.Avvist(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            attesteringer = attesteringer,
            datoKlageMottatt = datoKlageMottatt,
            fritekstTilAvvistVedtaksbrev = fritekstTilBrev,
        )
    } else {
        VilkårsvurdertKlage.Bekreftet.TilVurdering(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            vurderinger = vurderinger,
            attesteringer = attesteringer,
            datoKlageMottatt = datoKlageMottatt,
            klageinstanshendelser = klageinstanshendelser,
        )
    }
}
