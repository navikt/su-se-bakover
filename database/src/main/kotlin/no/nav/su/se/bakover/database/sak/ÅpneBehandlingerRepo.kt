package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.tidspunktOrNull
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.UUID

internal class ÅpneBehandlingerRepo(
    private val dbMetrics: DbMetrics,
) {
    /**
     * Henter åpne søknadsbehandlinger, åpne revurderinger, åpne klager, og nye søknader
     */
    fun hentÅpneBehandlinger(session: Session): List<Behandlingsoversikt> {
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
                              join revurdering r on r.sakid = sak.sakId
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
                it.toBehandlingsoversikt()
            }
        }
    }

    private fun Row.toBehandlingsoversikt(): Behandlingsoversikt {
        val behandlingstype = BehandlingsTypeDB.valueOf(string("type"))

        return Behandlingsoversikt(
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingsId = UUID.fromString(string("id")),
            behandlingstype = behandlingstype.toBehandlingstype(),
            status = hentÅpenBehandlingStatus(behandlingstype),
            behandlingStartet = tidspunktOrNull("opprettet"),
        )
    }

    private fun Row.hentÅpenBehandlingStatus(
        behandlingsTypeDB: BehandlingsTypeDB,
    ): Behandlingsoversikt.Behandlingsstatus {
        return when (behandlingsTypeDB) {
            BehandlingsTypeDB.SØKNAD -> Behandlingsoversikt.Behandlingsstatus.NY_SØKNAD
            BehandlingsTypeDB.SØKNADSBEHANDLING -> BehandlingsStatus.valueOf(string("status")).tilBehandlingsstatus()
            BehandlingsTypeDB.REVURDERING -> RevurderingsType.valueOf(string("status")).tilBehandlingsstatus()
            BehandlingsTypeDB.KLAGE -> KlagePostgresRepo.Tilstand.fromString(string("status")).tilBehandlingsstatus()
        }
    }
}

private fun BehandlingsStatus.tilBehandlingsstatus(): Behandlingsoversikt.Behandlingsstatus {
    return when (this) {
        BehandlingsStatus.OPPRETTET,
        BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
        BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
        BehandlingsStatus.BEREGNET_INNVILGET,
        BehandlingsStatus.BEREGNET_AVSLAG,
        BehandlingsStatus.SIMULERT,
        -> Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING

        BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
        BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
        -> Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING

        BehandlingsStatus.UNDERKJENT_INNVILGET,
        BehandlingsStatus.UNDERKJENT_AVSLAG,
        -> Behandlingsoversikt.Behandlingsstatus.UNDERKJENT

        BehandlingsStatus.IVERKSATT_INNVILGET,
        BehandlingsStatus.IVERKSATT_AVSLAG,
        -> throw IllegalStateException("Iverksatte søknadsbehandlinger er ikke en åpen behandling")
    }
}

private fun RevurderingsType.tilBehandlingsstatus(): Behandlingsoversikt.Behandlingsstatus {
    return when (this) {
        RevurderingsType.OPPRETTET,
        RevurderingsType.BEREGNET_INNVILGET,
        RevurderingsType.BEREGNET_OPPHØRT,
        RevurderingsType.BEREGNET_INGEN_ENDRING,
        RevurderingsType.SIMULERT_INNVILGET,
        RevurderingsType.SIMULERT_OPPHØRT,
        -> Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING

        RevurderingsType.SIMULERT_STANS,
        RevurderingsType.SIMULERT_GJENOPPTAK,
        RevurderingsType.TIL_ATTESTERING_INNVILGET,
        RevurderingsType.TIL_ATTESTERING_OPPHØRT,
        RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING,
        -> Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING

        RevurderingsType.UNDERKJENT_INNVILGET,
        RevurderingsType.UNDERKJENT_OPPHØRT,
        RevurderingsType.UNDERKJENT_INGEN_ENDRING,
        -> Behandlingsoversikt.Behandlingsstatus.UNDERKJENT

        RevurderingsType.IVERKSATT_STANS,
        RevurderingsType.IVERKSATT_GJENOPPTAK,
        RevurderingsType.IVERKSATT_INNVILGET,
        RevurderingsType.IVERKSATT_OPPHØRT,
        RevurderingsType.IVERKSATT_INGEN_ENDRING,
        -> throw IllegalStateException("Iverksatte revurderinger er ikke en åpen behandling")
    }
}

private fun KlagePostgresRepo.Tilstand.tilBehandlingsstatus(): Behandlingsoversikt.Behandlingsstatus {
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
        -> Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING

        KlagePostgresRepo.Tilstand.TIL_ATTESTERING_TIL_VURDERING,
        KlagePostgresRepo.Tilstand.TIL_ATTESTERING_AVVIST,
        -> Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING

        KlagePostgresRepo.Tilstand.OVERSENDT,
        KlagePostgresRepo.Tilstand.IVERKSATT_AVVIST,
        -> throw IllegalStateException("Iverksatte/Oversendte klager er ikke en åpen behandling")
    }
}

private enum class BehandlingsTypeDB {
    SØKNAD,
    SØKNADSBEHANDLING,
    REVURDERING,
    KLAGE,
    ;

    fun toBehandlingstype(): Behandlingsoversikt.Behandlingstype {
        return when (this) {
            SØKNAD -> Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING
            SØKNADSBEHANDLING -> Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING
            REVURDERING -> Behandlingsoversikt.Behandlingstype.REVURDERING
            KLAGE -> Behandlingsoversikt.Behandlingstype.KLAGE
        }
    }
}
