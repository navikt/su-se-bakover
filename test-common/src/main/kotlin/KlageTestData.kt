@file:Suppress("unused")

package no.nav.su.se.bakover.test

import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mapSecond
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
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
        ).getOrFail()

        if (vilkårsvurdertKlage !is VilkårsvurdertKlage.Påbegynt) throw IllegalStateException("Forventet en Vilkårsvurdert.Påbegynt, men fikk ${vilkårsvurdertKlage::class} ved oppretting av test data")

        Pair(
            sak.copy(klager = sak.klager.filterNot { it.id == vilkårsvurdertKlage.id } + vilkårsvurdertKlage),
            vilkårsvurdertKlage,
        )
    }
}

fun utfyltVilkårsvurdertKlageTilVurdering(
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
): Pair<Sak, VilkårsvurdertKlage.Utfylt.TilVurdering> {
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
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun utfyltAvvistVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
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
                vedtakId, innenforFristen, klagesDetPåKonkreteElementerIVedtaket, erUnderskrevet, begrunnelse,
            ),
        ).getOrFail()

        if (klage !is VilkårsvurdertKlage.Utfylt.Avvist) throw IllegalStateException("Forventet en Vilkårsvurdert.Utfylt(Avvist), men fikk ${klage::class} ved oppretting av test-data")

        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun bekreftetVilkårsvurdertKlageTilVurdering(
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
): Pair<Sak, VilkårsvurdertKlage.Bekreftet.TilVurdering> {
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
        ).orNull()!!

        if (klage !is VilkårsvurdertKlage.Bekreftet.TilVurdering) throw IllegalStateException("Forventet en Vilkårsvurdert.Bekreftet(TilVurdering), men fikk ${klage::class} ved oppretting av test-data")

        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun bekreftetAvvistVilkårsvurdertKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
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

fun avvistKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveId: OppgaveId = OppgaveId("klageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "dette er en fritekst med person opplysninger",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, AvvistKlage> {
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
        val klage = it.second.leggTilAvvistFritekstTilBrev(
            saksbehandler, fritekstTilBrev,
        ).getOrFail()

        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
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
    return bekreftetVurdertKlage().mapSecond {
        it.avslutt(
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).orNull()!!
    }
}

fun vurdertKlageTilAttestering(
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
        ).orNull()!!

        if (klage !is KlageTilAttestering.Vurdert) throw IllegalStateException("Forventet en KlageTilAttestering(TilVurdering) ved opprettelse av test data. Fikk ${klage::class}")
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun avvistKlageTilAttestering(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "dette er en fritekst med person opplysninger",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
): Pair<Sak, KlageTilAttestering.Avvist> {
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
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun underkjentKlageTilVurdering(
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
        ) { underkjentKlageOppgaveId.right() }.orNull()!!

        if (klage !is VurdertKlage.Bekreftet) throw IllegalStateException("Forventet en VurdertKlage.Bekreftet. Fikk ${klage::class} ved opprettelse av test-data.")

        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun underkjentAvvistKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    underkjentKlageOppgaveId: OppgaveId = OppgaveId("underkjentKlageOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("attestant"),
    attesteringsgrunn: Attestering.Underkjent.Grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
    attesteringskommentar: String = "attesteringskommentar",
): Pair<Sak, AvvistKlage> {
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
        ) { underkjentKlageOppgaveId.right() }.orNull()!!

        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun underkjentTilVurderingKlageTilAttestering(
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
        ).orNull()!!
        Pair(
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}

fun oversendtKlage(
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
): Pair<Sak, OversendtKlage> {
    return vurdertKlageTilAttestering(
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
        val klage = it.second.oversend(
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

fun iverksattAvvistKlage(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    journalpostId: JournalpostId = JournalpostId("klageJournalpostId"),
    oppgaveIdTilAttestering: OppgaveId = OppgaveId("klageTilAttesteringOppgaveId"),
    saksbehandler: NavIdentBruker.Saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
    datoKlageMottatt: LocalDate = 1.desember(2021),
    vedtakId: UUID = UUID.randomUUID(),
    innenforFristen: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.NEI,
    klagesDetPåKonkreteElementerIVedtaket: Boolean = true,
    erUnderskrevet: VilkårsvurderingerTilKlage.Svarord = VilkårsvurderingerTilKlage.Svarord.JA,
    begrunnelse: String = "begrunnelse",
    fritekstTilBrev: String = "dette er en fritekst med person opplysninger",
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("attestant"),
    sakMedVedtak: Sak = vedtakSøknadsbehandlingIverksattInnvilget().first,
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
            it.first.copy(klager = it.first.klager.filterNot { it.id == klage.id } + klage),
            klage,
        )
    }
}
