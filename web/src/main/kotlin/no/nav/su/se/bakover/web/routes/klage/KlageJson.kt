package no.nav.su.se.bakover.web.routes.klage

import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstansvedtak
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.web.routes.klage.KlageJson.VedtaksvurderingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.klage.Typer.Companion.frontendStatus
import no.nav.su.se.bakover.web.routes.klage.UtfallJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.klage.ÅrsakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson

internal data class KlageJson(
    val id: String,
    val sakid: String,
    val opprettet: String,
    val journalpostId: String,
    val saksbehandler: String,
    val datoKlageMottatt: String,
    val status: String,
    val vedtakId: String?,
    val innenforFristen: String?,
    val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
    val erUnderskrevet: String?,
    val begrunnelse: String?,
    val fritekstTilBrev: String?,
    val vedtaksvurdering: VedtaksvurderingJson?,
    val attesteringer: List<AttesteringJson>,
    val klagevedtakshistorikk: List<VedtattUtfallJson>,
) {
    data class VedtaksvurderingJson(
        val type: String,
        val omgjør: OmgjørJson?,
        val oppretthold: OpprettholdJson?,
    ) {

        enum class Type {
            OMGJØR,
            OPPRETTHOLD;
        }

        data class OmgjørJson(
            val årsak: String?,
            val utfall: String?,
        )

        data class OpprettholdJson(
            val hjemler: List<String>,
        )

        companion object {
            fun VurderingerTilKlage.Vedtaksvurdering.toJson(): VedtaksvurderingJson {
                return when (this) {
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Omgjør -> VedtaksvurderingJson(
                        type = Type.OMGJØR.toString(),
                        omgjør = OmgjørJson(
                            årsak = årsak?.toJson(),
                            utfall = utfall?.toJson(),
                        ),
                        oppretthold = null,
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Oppretthold -> VedtaksvurderingJson(
                        type = Type.OPPRETTHOLD.toString(),
                        omgjør = null,
                        oppretthold = OpprettholdJson(
                            hjemler = hjemler.map { it.toString() },
                        ),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Omgjør -> VedtaksvurderingJson(
                        type = Type.OMGJØR.toString(),
                        omgjør = OmgjørJson(
                            årsak = årsak.toJson(),
                            utfall = utfall.toJson(),
                        ),
                        oppretthold = null,
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold -> VedtaksvurderingJson(
                        type = Type.OPPRETTHOLD.toString(),
                        omgjør = null,
                        oppretthold = OpprettholdJson(
                            hjemler = hjemler.map { it.toString() },
                        ),
                    )
                }
            }
        }
    }

    data class VedtattUtfallJson(
        val utfall: String,
        val opprettet: String,
    )
}

internal fun Klage.toJson(): KlageJson {
    return when (this) {
        is OpprettetKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = emptyList(),
            klagevedtakshistorikk = emptyList(),
        )
        is VilkårsvurdertKlage.Påbegynt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId?.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = emptyList(),
        )
        is VilkårsvurdertKlage.Utfylt.TilVurdering -> this.mapUtfyltOgBekreftetTilKlageJson()
        is VilkårsvurdertKlage.Utfylt.Avvist -> this.mapUtfyltOgBekreftetTilKlageJson()
        is VilkårsvurdertKlage.Bekreftet.TilVurdering -> this.mapUtfyltOgBekreftetTilKlageJson()
        is VilkårsvurdertKlage.Bekreftet.Avvist -> this.mapUtfyltOgBekreftetTilKlageJson()
        is VurdertKlage.Påbegynt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering?.toJson(),
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = klagevedtakshistorikk.map { it.toJson() },
        )
        is VurdertKlage.Utfylt -> this.mapUtfyltOgBekreftetTilKlageJson()
        is VurdertKlage.Bekreftet -> this.mapUtfyltOgBekreftetTilKlageJson()
        is AvvistKlage -> this.mapPåbegyntOgBekreftetTilKlageJson()
        is KlageTilAttestering.Vurdert -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = klagevedtakshistorikk.map { it.toJson() }
        )
        is KlageTilAttestering.Avvist -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.fritekstTilBrev,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = emptyList(),
        )
        is OversendtKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilBrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = klagevedtakshistorikk.map { it.toJson() },
        )
        is IverksattAvvistKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = this.frontendStatus(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.fritekstTilBrev,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = emptyList(),
        )
    }
}

private enum class Typer(val verdi: String) {

    /**
     * Dette er den første tilstanden som man kommer i når man har opprettet en ny klage.
     * Man kan ikke gå tilbake fra denne tilstanden.
     * Man skal ikke komme tilbake til dette steget.
     * Man går fram til VILKÅRSVURDERT_PÅBEGYNT dersom man fyller ut ingen eller N-1 vilkårsvurderinger.
     * Man går fram til VILKÅRSVURDERT_UTFYLT dersom man fyller ut vilkårsvurderinger.
     */
    OPPRETTET("OPPRETTET"),

    /**
     * Man kommer i denne tilstanden når man fyller ut ingen eller N-1 vilkårsvurderinger.
     * Man kan ikke gå tilbake fra denne tilstanden.
     * Man går fram til VILKÅRSVURDERT_UTFYLT dersom man fyller ut alle vilkårsvurderingene.
     */
    VILKÅRSVURDERT_PÅBEGYNT("VILKÅRSVURDERT_PÅBEGYNT"),

    /**
     * Man kommer i denne tilstanden når man fyller ut alle vilkårsvurderingene OK.
     * Kan kun gå tilbake til tilstanden VILKÅRSVURDERT_PÅBEGYNT.
     * Går fram til VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING etter man 'bekrefter'.
     */
    VILKÅRSVURDERT_UTFYLT_TIL_VURDERING("VILKÅRSVURDERT_UTFYLT_TIL_VURDERING"),

    /**
     * Man kommer i denne tilstanden når man fyller ut alle vilkårsvurderingene der minst 1 at svarene
     * er besvart 'nei/false'.
     * Kan kun gå tilbake til tilstanden VILKÅRSVURDERT_PÅBEGYNT.
     * Går fram til VILKÅRSVURDERT_BEKREFTET_AVVIST etter man 'bekrefter'.
     */
    VILKÅRSVURDERT_UTFYLT_AVVIST("VILKÅRSVURDERT_UTFYLT_AVVIST"),

    /**
     * Man kommer i denne tilstanden dersom man bekrefter VILKÅRSVURDERT_UTFYLT_TIL_VURDERING
     * Kan gå tilbake til VILKÅRSVURDERT_PÅBEGYNT
     * Går fram til VURDERT_PÅBEGYNT eller VURDERT_UTFYLT
     */
    VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING("VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING"),

    /**
     * Man kommer i denne tilstanden dersom man bekrefter VILKÅRSVURDERT_UTFYLT_AVVIST
     * Kan gå tilbake til VILKÅRSVURDERT_PÅBEGYNT
     * Går fram til TIL_ATTESTERING_AVVIST
     */
    VILKÅRSVURDERT_BEKREFTET_AVVIST("VILKÅRSVURDERT_BEKREFTET_AVVIST"),

    /**
     * Man kommer i denne tilstanden dersom man begynner å vurdere (påbegynt/ferdig) etter man er i tilstanden VILKÅRSVURDERT_BEKREFTET
     * Kan gå tilbake til VILKÅRSVURDERT_PÅBEGYNT dersom man fyller ut ingen eller N-1 vilkårsvurderinger.
     * Kan gå fram til VURDERT_UTFYLT dersom man fyller ut alle vurderingene.
     */
    VURDERT_PÅBEGYNT("VURDERT_PÅBEGYNT"),

    /**
     * Man kommer i denne tilstanden etter vilkårsvurderingene er bekreftet og alle vurderingene er utfylt.
     * Kan gå tilbake til VURDERT_PÅBEGYNT dersom man fjerner en eller flere vurderinger
     * Kan gå tilbake til VILKÅRSVURDERT_PÅBEGYNT dersom man fjerner en eller flere vilkårsvurderinger.
     * Kan gå fram til VURDERT_BEKREFTET dersom alle vilkårsvurderingene er utfylt og bekreftet og vurderingene er utfylt og bekreftet.
     */
    VURDERT_UTFYLT("VURDERT_UTFYLT"),

    /**
     * Alias oppsummeringssteget (for vurderte klager).
     * Man kommer i denne tilstanden dersom alle vilkårsvurderingene er utfylt og bekreftet og vurderingene er utfylt og bekreftet.
     * Kan gå fram til TIL_ATTESTERING
     */
    VURDERT_BEKREFTET("VURDERT_BEKREFTET"),

    /**
     * Man kommer i denne tilstanden etter at man har bekreftet vilkårsvurderingene, der minst et av svarene er avvist,
     * og har lagret en midlertidig tilstand av brev-friteksten
     * Kan gå fram til TIL_ATTESTERING_AVVIST
     */
    AVVIST("AVVIST"),

    /**
     * Man kommer i denne tilstanden dersom man var i VURDERT_BEKREFTET og valgte å sende til attestering.
     * Kan gå tilbake til VURDERT_BEKREFTET (underkjent)
     * Kan gå fram til OVERSENDT
     */
    TIL_ATTESTERING_TIL_VURDERING("TIL_ATTESTERING_TIL_VURDERING"),

    /**
     * Man kommer i denne tilstanden dersom man var i VILKÅRSVURDERT_BEKREFTET og valgte å sende til attestering.
     * Kan gå tilbake til AVVIST_BEKREFTET (underkjent)
     * Kan gå fram til AVVIST
     */
    TIL_ATTESTERING_AVVIST("TIL_ATTESTERING_AVVIST"),

    /**
     * Man kommer i denne tilstanden dersom man var i TIL_ATTESTERING og valgte å iverksette.
     * Dette er en endelig tilstand.
     * Man kommer ikke tilbake fra denne tilstanden.
     */
    OVERSENDT("OVERSENDT"),

    /**
     * Man kommer i denne tilstanden dersom man var i TIL_ATTESTERING_AVVIST og valgte å iverksette.
     * Dette er en endelig tilstand.
     * Man kommer ikke tilbake fra denne tilstanden
     */
    IVERKSATT_AVVIST("IVERKSATT_AVVIST");

    companion object {
        fun Klage.frontendStatus(): String {
            return when (this) {
                is OpprettetKlage -> OPPRETTET

                is VilkårsvurdertKlage.Påbegynt -> VILKÅRSVURDERT_PÅBEGYNT
                is VilkårsvurdertKlage.Utfylt.TilVurdering -> VILKÅRSVURDERT_UTFYLT_TIL_VURDERING
                is VilkårsvurdertKlage.Utfylt.Avvist -> VILKÅRSVURDERT_UTFYLT_AVVIST
                is VilkårsvurdertKlage.Bekreftet.TilVurdering -> VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING
                is VilkårsvurdertKlage.Bekreftet.Avvist -> VILKÅRSVURDERT_BEKREFTET_AVVIST

                is VurdertKlage.Påbegynt -> VURDERT_PÅBEGYNT
                is VurdertKlage.Utfylt -> VURDERT_UTFYLT
                is VurdertKlage.Bekreftet -> VURDERT_BEKREFTET

                is AvvistKlage -> AVVIST

                is KlageTilAttestering.Vurdert -> TIL_ATTESTERING_TIL_VURDERING
                is KlageTilAttestering.Avvist -> TIL_ATTESTERING_AVVIST

                is OversendtKlage -> OVERSENDT

                is IverksattAvvistKlage -> IVERKSATT_AVVIST
            }.toString()
        }
    }

    override fun toString() = verdi
}

enum class ÅrsakJson {
    FEIL_LOVANVENDELSE,
    ULIK_SKJØNNSVURDERING,
    SAKSBEHANDLINGSFEIL,
    NYTT_FAKTUM;

    companion object {
        fun VurderingerTilKlage.Vedtaksvurdering.Årsak.toJson(): String {
            return when (this) {
                VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE -> FEIL_LOVANVENDELSE
                VurderingerTilKlage.Vedtaksvurdering.Årsak.ULIK_SKJØNNSVURDERING -> ULIK_SKJØNNSVURDERING
                VurderingerTilKlage.Vedtaksvurdering.Årsak.SAKSBEHANDLINGSFEIL -> SAKSBEHANDLINGSFEIL
                VurderingerTilKlage.Vedtaksvurdering.Årsak.NYTT_FAKTUM -> NYTT_FAKTUM
            }.toString()
        }
    }
}

enum class UtfallJson {
    TIL_GUNST,
    TIL_UGUNST;

    companion object {
        fun VurderingerTilKlage.Vedtaksvurdering.Utfall.toJson(): String {
            return when (this) {
                VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST -> TIL_GUNST
                VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST -> TIL_UGUNST
            }.toString()
        }
    }
}

private fun VilkårsvurdertKlage.mapUtfyltOgBekreftetTilKlageJson(): KlageJson {
    if (this !is VilkårsvurdertKlage.Utfylt && this !is VilkårsvurdertKlage.Bekreftet) {
        throw IllegalCallerException("Prøver å mappe til klageJson i feil tilstand. Id: $id. tilstand: ${this::class}")
    }
    return KlageJson(
        id = this.id.toString(),
        sakid = this.sakId.toString(),
        opprettet = this.opprettet.toString(),
        journalpostId = this.journalpostId.toString(),
        saksbehandler = this.saksbehandler.navIdent,
        datoKlageMottatt = this.datoKlageMottatt.toString(),
        status = this.frontendStatus(),
        vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
        innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
        klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
        begrunnelse = this.vilkårsvurderinger.begrunnelse,
        fritekstTilBrev = null,
        vedtaksvurdering = null,
        attesteringer = this.attesteringer.toJson(),
        klagevedtakshistorikk = when (this) {
            is AvvistKlage -> emptyList()
            is VilkårsvurdertKlage.Bekreftet.Avvist -> emptyList()
            is VilkårsvurdertKlage.Bekreftet.TilVurdering -> klagevedtakshistorikk.map { it.toJson() }
            is VilkårsvurdertKlage.Påbegynt -> emptyList()
            is VilkårsvurdertKlage.Utfylt.Avvist -> emptyList()
            is VilkårsvurdertKlage.Utfylt.TilVurdering -> klagevedtakshistorikk.map { it.toJson() }
        },
    )
}

private fun VurdertKlage.mapUtfyltOgBekreftetTilKlageJson(): KlageJson {
    if (this !is VurdertKlage.Utfylt && this !is VurdertKlage.Bekreftet) {
        throw IllegalCallerException("Prøver å mappe til klageJson i feil tilstand. Id: $id. tilstand: ${this::class}")
    }
    return KlageJson(
        id = this.id.toString(),
        sakid = this.sakId.toString(),
        opprettet = this.opprettet.toString(),
        journalpostId = this.journalpostId.toString(),
        saksbehandler = this.saksbehandler.navIdent,
        datoKlageMottatt = this.datoKlageMottatt.toString(),
        status = this.frontendStatus(),
        vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
        innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
        klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
        begrunnelse = this.vilkårsvurderinger.begrunnelse,
        fritekstTilBrev = this.vurderinger.fritekstTilBrev,
        // Vedtaksvurderingen kan ikke være null dersom klagen er utfylt / bekreftet
        vedtaksvurdering = this.vurderinger.vedtaksvurdering!!.toJson(),
        attesteringer = this.attesteringer.toJson(),
        klagevedtakshistorikk = klagevedtakshistorikk.map { it.toJson() },
    )
}

private fun AvvistKlage.mapPåbegyntOgBekreftetTilKlageJson(): KlageJson {
    return KlageJson(
        id = this.id.toString(),
        sakid = this.sakId.toString(),
        opprettet = this.opprettet.toString(),
        journalpostId = this.journalpostId.toString(),
        saksbehandler = this.saksbehandler.navIdent,
        datoKlageMottatt = this.datoKlageMottatt.toString(),
        status = this.frontendStatus(),
        vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
        innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
        klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
        begrunnelse = this.vilkårsvurderinger.begrunnelse,
        fritekstTilBrev = this.fritekstTilBrev,
        vedtaksvurdering = null,
        attesteringer = this.attesteringer.toJson(),
        klagevedtakshistorikk = emptyList(),
    )
}

internal fun ProsessertKlageinstansvedtak.toJson(): KlageJson.VedtattUtfallJson {
    return KlageJson.VedtattUtfallJson(
        utfall = when (this.utfall) {
            KlagevedtakUtfall.TRUKKET -> "TRUKKET"
            KlagevedtakUtfall.RETUR -> "RETUR"
            KlagevedtakUtfall.OPPHEVET -> "OPPHEVET"
            KlagevedtakUtfall.MEDHOLD -> "MEDHOLD"
            KlagevedtakUtfall.DELVIS_MEDHOLD -> "DELVIS_MEDHOLD"
            KlagevedtakUtfall.STADFESTELSE -> "STADFESTELSE"
            KlagevedtakUtfall.UGUNST -> "UGUNST"
            KlagevedtakUtfall.AVVIST -> "AVVIST"
        },
        opprettet = this.opprettet.toString()
    )
}
