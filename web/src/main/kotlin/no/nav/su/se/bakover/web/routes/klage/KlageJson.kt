package no.nav.su.se.bakover.web.routes.klage

import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.web.routes.klage.KlageJson.Avsluttet.Companion.utledAvsluttet
import no.nav.su.se.bakover.web.routes.klage.KlageJson.VedtaksvurderingJson.Companion.toJson
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
    val avsluttet: Avsluttet,
) {
    enum class Avsluttet {
        KAN_AVSLUTTES,
        ER_AVSLUTTET,
        KAN_IKKE_AVSLUTTES;

        companion object {
            internal fun Klage.utledAvsluttet(): Avsluttet = when {
                this is AvsluttetKlage -> ER_AVSLUTTET
                kanAvsluttes() -> KAN_AVSLUTTES
                else -> KAN_IKKE_AVSLUTTES
            }
        }
    }

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
    val avsluttet = utledAvsluttet()
    return when (this) {
        is OpprettetKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = Typer.OPPRETTET.toString(),
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = emptyList(),
            klagevedtakshistorikk = emptyList(),
            avsluttet = avsluttet,
        )
        is VilkårsvurdertKlage.Påbegynt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = Typer.VILKÅRSVURDERT_PÅBEGYNT.toString(),
            vedtakId = this.vilkårsvurderinger.vedtakId?.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen?.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet?.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = null,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = emptyList(),
            avsluttet = avsluttet,
        )
        is VilkårsvurdertKlage.Utfylt.TilVurdering -> this.mapUtfyltOgBekreftetTilKlageJson(
            status = Typer.VILKÅRSVURDERT_UTFYLT_TIL_VURDERING.toString(),
            klagevedtakshistorikk = klageinstanshendelser.map { it.toJson() },
            avsluttet = avsluttet,
        )
        is VilkårsvurdertKlage.Utfylt.Avvist -> this.mapUtfyltOgBekreftetTilKlageJson(
            status = Typer.VILKÅRSVURDERT_UTFYLT_AVVIST.toString(),
            klagevedtakshistorikk = emptyList(),
            avsluttet = avsluttet,
        )
        is VilkårsvurdertKlage.Bekreftet.TilVurdering -> this.mapUtfyltOgBekreftetTilKlageJson(
            status = Typer.VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING.toString(),
            klagevedtakshistorikk = klageinstanshendelser.map { it.toJson() },
            avsluttet = avsluttet,
        )
        is VilkårsvurdertKlage.Bekreftet.Avvist -> this.mapUtfyltOgBekreftetTilKlageJson(
            status = Typer.VILKÅRSVURDERT_BEKREFTET_AVVIST.toString(),
            klagevedtakshistorikk = emptyList(),
            avsluttet = avsluttet,
        )
        is VurdertKlage.Påbegynt -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = Typer.VURDERT_PÅBEGYNT.toString(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilOversendelsesbrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering?.toJson(),
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = klageinstanshendelser.map { it.toJson() },
            avsluttet = avsluttet,
        )
        is VurdertKlage.Utfylt -> this.mapUtfyltOgBekreftetTilKlageJson(Typer.VURDERT_UTFYLT.toString(), avsluttet)
        is VurdertKlage.Bekreftet -> this.mapUtfyltOgBekreftetTilKlageJson(
            status = Typer.VURDERT_BEKREFTET.toString(),
            avsluttet = avsluttet,
        )
        is AvvistKlage -> this.mapPåbegyntOgBekreftetTilKlageJson(Typer.AVVIST.toString(), avsluttet)
        is KlageTilAttestering.Vurdert -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = Typer.TIL_ATTESTERING_TIL_VURDERING.toString(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilOversendelsesbrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = klageinstanshendelser.map { it.toJson() },
            avsluttet = avsluttet,
        )
        is KlageTilAttestering.Avvist -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = Typer.TIL_ATTESTERING_AVVIST.toString(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.fritekstTilVedtaksbrev,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = emptyList(),
            avsluttet = avsluttet,
        )
        is OversendtKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = Typer.OVERSENDT.toString(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen
                .toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.vurderinger.fritekstTilOversendelsesbrev,
            vedtaksvurdering = this.vurderinger.vedtaksvurdering.toJson(),
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = klageinstanshendelser.map { it.toJson() },
            avsluttet = avsluttet,
        )
        is IverksattAvvistKlage -> KlageJson(
            id = this.id.toString(),
            sakid = this.sakId.toString(),
            opprettet = this.opprettet.toString(),
            journalpostId = this.journalpostId.toString(),
            saksbehandler = this.saksbehandler.navIdent,
            datoKlageMottatt = this.datoKlageMottatt.toString(),
            status = Typer.IVERKSATT_AVVIST.toString(),
            vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
            innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
            klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
            erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
            begrunnelse = this.vilkårsvurderinger.begrunnelse,
            fritekstTilBrev = this.fritekstTilVedtaksbrev,
            vedtaksvurdering = null,
            attesteringer = this.attesteringer.toJson(),
            klagevedtakshistorikk = emptyList(),
            avsluttet = avsluttet,
        )
        is AvsluttetKlage -> this.hentUnderliggendeKlage().toJson().copy(
            avsluttet = KlageJson.Avsluttet.ER_AVSLUTTET,
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

private fun VilkårsvurdertKlage.mapUtfyltOgBekreftetTilKlageJson(
    status: String,
    klagevedtakshistorikk: List<KlageJson.VedtattUtfallJson>,
    avsluttet: KlageJson.Avsluttet,
): KlageJson {
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
        status = status,
        vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
        innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
        klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
        begrunnelse = this.vilkårsvurderinger.begrunnelse,
        fritekstTilBrev = when (this) {
            is VilkårsvurdertKlage.Påbegynt -> null
            is VilkårsvurdertKlage.Bekreftet.Avvist -> this.fritekstTilAvvistVedtaksbrev
            is VilkårsvurdertKlage.Bekreftet.TilVurdering -> this.vurderinger?.fritekstTilOversendelsesbrev
            is VilkårsvurdertKlage.Utfylt.Avvist -> this.fritekstTilVedtaksbrev
            is VilkårsvurdertKlage.Utfylt.TilVurdering -> this.vurderinger?.fritekstTilOversendelsesbrev
        },
        vedtaksvurdering = when (this) {
            is VilkårsvurdertKlage.Påbegynt -> null
            is VilkårsvurdertKlage.Bekreftet.Avvist -> null
            is VilkårsvurdertKlage.Bekreftet.TilVurdering -> this.vurderinger?.vedtaksvurdering?.toJson()
            is VilkårsvurdertKlage.Utfylt.Avvist -> null
            is VilkårsvurdertKlage.Utfylt.TilVurdering -> this.vurderinger?.vedtaksvurdering?.toJson()
        },
        attesteringer = this.attesteringer.toJson(),
        klagevedtakshistorikk = klagevedtakshistorikk,
        avsluttet = avsluttet,
    )
}

private fun VurdertKlage.mapUtfyltOgBekreftetTilKlageJson(
    status: String,
    avsluttet: KlageJson.Avsluttet,
): KlageJson {
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
        status = status,
        vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
        innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
        klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
        begrunnelse = this.vilkårsvurderinger.begrunnelse,
        fritekstTilBrev = this.vurderinger.fritekstTilOversendelsesbrev,
        // Vedtaksvurderingen kan ikke være null dersom klagen er utfylt / bekreftet
        vedtaksvurdering = this.vurderinger.vedtaksvurdering!!.toJson(),
        attesteringer = this.attesteringer.toJson(),
        klagevedtakshistorikk = klageinstanshendelser.map { it.toJson() },
        avsluttet = avsluttet,
    )
}

private fun AvvistKlage.mapPåbegyntOgBekreftetTilKlageJson(
    status: String,
    avsluttet: KlageJson.Avsluttet,
): KlageJson {
    return KlageJson(
        id = this.id.toString(),
        sakid = this.sakId.toString(),
        opprettet = this.opprettet.toString(),
        journalpostId = this.journalpostId.toString(),
        saksbehandler = this.saksbehandler.navIdent,
        datoKlageMottatt = this.datoKlageMottatt.toString(),
        status = status,
        vedtakId = this.vilkårsvurderinger.vedtakId.toString(),
        innenforFristen = this.vilkårsvurderinger.innenforFristen.toString(),
        klagesDetPåKonkreteElementerIVedtaket = this.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
        erUnderskrevet = this.vilkårsvurderinger.erUnderskrevet.toString(),
        begrunnelse = this.vilkårsvurderinger.begrunnelse,
        fritekstTilBrev = this.fritekstTilVedtaksbrev,
        vedtaksvurdering = null,
        attesteringer = this.attesteringer.toJson(),
        klagevedtakshistorikk = emptyList(),
        avsluttet = avsluttet,
    )
}

internal fun ProsessertKlageinstanshendelse.toJson(): KlageJson.VedtattUtfallJson {
    return KlageJson.VedtattUtfallJson(
        utfall = when (this.utfall) {
            KlageinstansUtfall.TRUKKET -> "TRUKKET"
            KlageinstansUtfall.RETUR -> "RETUR"
            KlageinstansUtfall.OPPHEVET -> "OPPHEVET"
            KlageinstansUtfall.MEDHOLD -> "MEDHOLD"
            KlageinstansUtfall.DELVIS_MEDHOLD -> "DELVIS_MEDHOLD"
            KlageinstansUtfall.STADFESTELSE -> "STADFESTELSE"
            KlageinstansUtfall.UGUNST -> "UGUNST"
            KlageinstansUtfall.AVVIST -> "AVVIST"
        },
        opprettet = this.opprettet.toString(),
    )
}
