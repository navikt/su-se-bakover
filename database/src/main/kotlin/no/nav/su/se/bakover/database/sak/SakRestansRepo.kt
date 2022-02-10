package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.tidspunktOrNull
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.SakBehandlinger
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.UUID

// TODO: navn
internal class SakRestansRepo(
    private val dbMetrics: DbMetrics,
) {
    /**
     * Henter åpne søknadsbehandlinger, åpne revurderinger, og nye søknader
     */
    fun hentÅpneBehandlinger(session: Session): List<SakBehandlinger.ÅpenBehandling> {
        return dbMetrics.timeQuery("hentÅpneBehandlinger") {
            //language=sql
            """
                with sak as (
                select id as sakId, saksnummer
                from sak
            ),
                 behandlinger as (
                     select sak.sakId, sak.saksnummer, b.id, b.opprettet, b.status as status, 'SØKNADSBEHANDLING' as type
                     from sak
                              join behandling b on b.sakid = sak.sakId
                     where b.status not like ('IVERKSATT%') and b.lukket = false
                 ),
                 revurderinger as (
                     select sak.sakId, sak.saksnummer, r.id, r.opprettet, r.revurderingstype as status, 'REVURDERING' as type
                     from sak
                              join behandling_vedtak bv on bv.sakid = sak.sakId
                              join revurdering r on r.vedtaksomrevurderesid = bv.vedtakid
                     where r.revurderingstype not like ('IVERKSATT%') and r.avsluttet is null
                 ),
                 klage as (
                     select sak.sakId, sak.saksnummer, k.id, k.opprettet, k.type as status, 'KLAGE' as type
                     from sak
                              join klage k on sak.sakId = k.sakid
                     where k.type not like ('iverksatt%') and k.type not like 'oversendt' and k.avsluttet is null
                 ),
                 søknader as (
                     select 
                        sak.sakId, 
                        sak.saksnummer, 
                        s.id,
                        null::timestamp as opprettet,
                        'NY_SØKNAD' as status, 
                        'SØKNAD' as type
                     from sak
                              join søknad s on sak.sakId = s.sakid
                     where s.lukket is null
                       and not exists(select 1 from behandling where søknadid = s.id)
                 ),
                 slåttSammen as (
                     select *
                     from søknader
                     union
                     select *
                     from behandlinger
                     union
                     select *
                     from revurderinger
                     union
                     select *
                     from klage
                 )
            select *
            from slåttSammen
                        """.hentListe(emptyMap(), session) {
                it.toÅpenBehandling()
            }
        }
    }

    fun hentFerdigeBehandlinger(session: Session): List<SakBehandlinger.FerdigBehandling> {
        // TODO - grafana ting
        //language=sql
        return """
            with sak as (
                select id as sakId, saksnummer
                from sak
            ),
                 behandlinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            b.id,
                            b.opprettet,
                            b.status            as resultat,
                            'SØKNADSBEHANDLING' as type,
                            b.lukket            as avsluttet
                     from sak
                              join behandling b on b.sakid = sak.sakId
                     where b.status like ('IVERKSATT%')
                        or b.lukket = true
                 ),
                 revurderinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            r.id,
                            r.opprettet,
                            r.revurderingstype                                         as resultat,
                            'REVURDERING'                                              as type,
                            case when r.avsluttet is not null then true else false end as avsluttet
                     from sak
                              join behandling_vedtak bv on bv.sakid = sak.sakId
                              join revurdering r on r.vedtaksomrevurderesid = bv.vedtakid
                     where r.revurderingstype like ('IVERKSATT%')
                        or r.avsluttet is not null
                 ),
                 klage as (
                     select sak.sakId,
                            sak.saksnummer,
                            k.id,
                            k.opprettet,
                            k.type                                                     as resultat,
                            'KLAGE'                                                    as type,
                            case when k.avsluttet is not null then true else false end as avsluttet
                     from sak
                              join klage k on sak.sakId = k.sakid
                     where k.type like ('iverksatt%')
                        or k.type like 'oversendt'
                        or k.avsluttet is not null
                 ),
                 slåttSammen as (
                     select *
                     from behandlinger
                     union
                     select *
                     from revurderinger
                     union
                     select *
                     from klage
                 )
            select *
            from slåttSammen
        """.trimIndent().hentListe(emptyMap(), session) {
            it.toFerdigBehandling()
        }
    }

    private fun Row.toÅpenBehandling(): SakBehandlinger.ÅpenBehandling {
        val hentetRestansType = RestansTypeDB.valueOf(string("type"))

        return SakBehandlinger.ÅpenBehandling(
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingsId = UUID.fromString(string("id")),
            restansType = hentetRestansType.toRestansType(),
            status = hentÅpenBehandlingStatus(hentetRestansType),
            behandlingStartet = tidspunktOrNull("opprettet"),
        )
    }

    private fun Row.toFerdigBehandling(): SakBehandlinger.FerdigBehandling {
        val hentetRestansType = RestansTypeDB.valueOf(string("type"))

        val erAvsluttet = boolean("avsluttet")

        return SakBehandlinger.FerdigBehandling(
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingsId = UUID.fromString(string("id")),
            restansType = hentetRestansType.toRestansType(),
            result = hentFerdigBehandlingResultat(hentetRestansType, erAvsluttet),
            behandlingStartet = tidspunkt("opprettet"),
        )
    }

    private fun Row.hentÅpenBehandlingStatus(
        restansType: RestansTypeDB,
    ): SakBehandlinger.ÅpenBehandling.RestansStatus {
        return when (restansType) {
            RestansTypeDB.SØKNAD -> SakBehandlinger.ÅpenBehandling.RestansStatus.NY_SØKNAD
            RestansTypeDB.SØKNADSBEHANDLING -> hentSøknadsbehandlingStatus()
            RestansTypeDB.REVURDERING -> hentRevurderingStatus()
            RestansTypeDB.KLAGE -> hentKlageStatus()
        }
    }

    private fun Row.hentFerdigBehandlingResultat(
        restansType: RestansTypeDB,
        erBehandlingAvsluttet: Boolean,
    ): SakBehandlinger.FerdigBehandling.RestansResultat {
        if (erBehandlingAvsluttet) {
            return SakBehandlinger.FerdigBehandling.RestansResultat.AVSLUTTET
        }
        return when (restansType) {
            RestansTypeDB.SØKNAD -> throw IllegalStateException("En søknad skal ikke hentes som en ferdig behandling. ")
            RestansTypeDB.SØKNADSBEHANDLING -> hentSøknadsbehandlingResultat()
            RestansTypeDB.REVURDERING -> hentRevurderingsResultat()
            RestansTypeDB.KLAGE -> hentKlageResultat()
        }
    }

    sealed class SakBehandlingStatuserOgResultat {
        class Status(val status: SakBehandlinger.ÅpenBehandling.RestansStatus) :
            SakBehandlingStatuserOgResultat()

        class Resultat(val resultat: SakBehandlinger.FerdigBehandling.RestansResultat) :
            SakBehandlingStatuserOgResultat()
    }

    private fun Row.hentSøknadsbehandlingStatus(): SakBehandlinger.ÅpenBehandling.RestansStatus {
        return when (
            val status = BehandlingsStatus.valueOf(string("status")).tilSakBehandlingStatusEllerResultat()
        ) {
            is SakBehandlingStatuserOgResultat.Resultat -> throw IllegalStateException("Kunne ikke hente åpen behandling fordi $status er av en ferdig behandling.")
            is SakBehandlingStatuserOgResultat.Status -> status.status
        }
    }

    private fun Row.hentSøknadsbehandlingResultat(): SakBehandlinger.FerdigBehandling.RestansResultat {
        return when (
            val behandlingsResultat =
                BehandlingsStatus.valueOf(string("resultat")).tilSakBehandlingStatusEllerResultat()
        ) {
            is SakBehandlingStatuserOgResultat.Resultat -> behandlingsResultat.resultat
            is SakBehandlingStatuserOgResultat.Status -> throw IllegalStateException("Kunne ikke hente ferdig behandling fordi $behandlingsResultat er av en åpen behandling.")
        }
    }

    private fun Row.hentRevurderingStatus(): SakBehandlinger.ÅpenBehandling.RestansStatus {
        return when (
            val status =
                RevurderingsType.valueOf(string("status")).tilSakBehandlingStatusEllerResultat()
        ) {
            is SakBehandlingStatuserOgResultat.Resultat -> throw IllegalStateException("Kunne ikke hente åpen behandling fordi $status er av en ferdig behandling.")
            is SakBehandlingStatuserOgResultat.Status -> status.status
        }
    }

    private fun Row.hentRevurderingsResultat(): SakBehandlinger.FerdigBehandling.RestansResultat {
        return when (
            val behandlingsResultat =
                RevurderingsType.valueOf(string("resultat")).tilSakBehandlingStatusEllerResultat()
        ) {
            is SakBehandlingStatuserOgResultat.Resultat -> behandlingsResultat.resultat
            is SakBehandlingStatuserOgResultat.Status -> throw IllegalStateException("Kunne ikke hente ferdig behandling fordi $behandlingsResultat er av en åpen behandling.")
        }
    }

    private fun Row.hentKlageStatus(): SakBehandlinger.ÅpenBehandling.RestansStatus {
        return when (
            val status =
                KlagePostgresRepo.Tilstand.fromString(string("status")).tilSakBehandlingStatusEllerResultat()
        ) {
            is SakBehandlingStatuserOgResultat.Resultat -> throw IllegalStateException("Kunne ikke hente åpen behandling fordi $status er av en ferdig behandling.")
            is SakBehandlingStatuserOgResultat.Status -> status.status
        }
    }

    private fun Row.hentKlageResultat(): SakBehandlinger.FerdigBehandling.RestansResultat {
        return when (
            val behandlingsResultat =
                KlagePostgresRepo.Tilstand.fromString(string("resultat")).tilSakBehandlingStatusEllerResultat()
        ) {
            is SakBehandlingStatuserOgResultat.Resultat -> behandlingsResultat.resultat
            is SakBehandlingStatuserOgResultat.Status -> throw IllegalStateException("Kunne ikke hente ferdig behandling fordi $behandlingsResultat er av en åpen behandling.")
        }
    }

    private fun BehandlingsStatus.tilSakBehandlingStatusEllerResultat(): SakBehandlingStatuserOgResultat {
        return when (this) {
            BehandlingsStatus.OPPRETTET,
            BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
            BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
            BehandlingsStatus.BEREGNET_INNVILGET,
            BehandlingsStatus.BEREGNET_AVSLAG,
            BehandlingsStatus.SIMULERT,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.UNDER_BEHANDLING)

            BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.TIL_ATTESTERING)

            BehandlingsStatus.UNDERKJENT_INNVILGET,
            BehandlingsStatus.UNDERKJENT_AVSLAG,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.UNDERKJENT)

            BehandlingsStatus.IVERKSATT_INNVILGET -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.INNVILGET)
            BehandlingsStatus.IVERKSATT_AVSLAG -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.AVSLAG)
        }
    }

    private fun RevurderingsType.tilSakBehandlingStatusEllerResultat(): SakBehandlingStatuserOgResultat {
        return when (this) {
            RevurderingsType.OPPRETTET,
            RevurderingsType.BEREGNET_INNVILGET,
            RevurderingsType.BEREGNET_OPPHØRT,
            RevurderingsType.BEREGNET_INGEN_ENDRING,
            RevurderingsType.SIMULERT_INNVILGET,
            RevurderingsType.SIMULERT_OPPHØRT,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.UNDER_BEHANDLING)

            RevurderingsType.SIMULERT_STANS,
            RevurderingsType.SIMULERT_GJENOPPTAK,
            RevurderingsType.TIL_ATTESTERING_INNVILGET,
            RevurderingsType.TIL_ATTESTERING_OPPHØRT,
            RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.TIL_ATTESTERING)

            RevurderingsType.UNDERKJENT_INNVILGET,
            RevurderingsType.UNDERKJENT_OPPHØRT,
            RevurderingsType.UNDERKJENT_INGEN_ENDRING,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.UNDERKJENT)

            RevurderingsType.IVERKSATT_STANS -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.AVSLAG)
            RevurderingsType.IVERKSATT_GJENOPPTAK -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.INNVILGET)
            RevurderingsType.IVERKSATT_INNVILGET -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.INNVILGET)
            RevurderingsType.IVERKSATT_OPPHØRT -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.OPPHØR)
            RevurderingsType.IVERKSATT_INGEN_ENDRING -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.INGEN_ENDRING)
        }
    }

    private fun KlagePostgresRepo.Tilstand.tilSakBehandlingStatusEllerResultat(): SakBehandlingStatuserOgResultat {
        return when (this) {
            KlagePostgresRepo.Tilstand.OPPRETTET,
            KlagePostgresRepo.Tilstand.VILKÅRSVURDERT_PÅBEGYNT,
            KlagePostgresRepo.Tilstand.VILKÅRSVURDERT_UTFYLT_TIL_VURDERING,
            KlagePostgresRepo.Tilstand.VILKÅRSVURDERT_UTFYLT_AVVIST,
            KlagePostgresRepo.Tilstand.VILKÅRSVURDERT_BEKREFTET_TIL_VURDERING,
            KlagePostgresRepo.Tilstand.VILKÅRSVURDERT_BEKREFTET_AVVIST,
            KlagePostgresRepo.Tilstand.VURDERT_PÅBEGYNT,
            KlagePostgresRepo.Tilstand.VURDERT_UTFYLT,
            KlagePostgresRepo.Tilstand.VURDERT_BEKREFTET,
            KlagePostgresRepo.Tilstand.AVVIST,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.UNDER_BEHANDLING)

            KlagePostgresRepo.Tilstand.TIL_ATTESTERING_TIL_VURDERING,
            KlagePostgresRepo.Tilstand.TIL_ATTESTERING_AVVIST,
            -> SakBehandlingStatuserOgResultat.Status(SakBehandlinger.ÅpenBehandling.RestansStatus.TIL_ATTESTERING)

            KlagePostgresRepo.Tilstand.OVERSENDT -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.INNVILGET)
            KlagePostgresRepo.Tilstand.IVERKSATT_AVVIST -> SakBehandlingStatuserOgResultat.Resultat(SakBehandlinger.FerdigBehandling.RestansResultat.AVSLAG)
        }
    }

    private enum class RestansTypeDB {
        SØKNAD,
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE;

        fun toRestansType(): SakBehandlinger.RestansType {
            return when (this) {
                SØKNAD -> SakBehandlinger.RestansType.SØKNADSBEHANDLING
                SØKNADSBEHANDLING -> SakBehandlinger.RestansType.SØKNADSBEHANDLING
                REVURDERING -> SakBehandlinger.RestansType.REVURDERING
                KLAGE -> SakBehandlinger.RestansType.KLAGE
            }
        }
    }
}
