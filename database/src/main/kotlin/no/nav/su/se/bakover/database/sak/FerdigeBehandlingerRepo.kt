package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.UUID

internal class FerdigeBehandlingerRepo(
    private val dbMetrics: DbMetrics,
) {

    /**
     * Innvilget, avslått, opphørt og avsluttede/lukkede behandlinger.
     */
    fun hentFerdigeBehandlinger(session: Session): List<Behandlingsoversikt> {
        return dbMetrics.timeQuery("hentFerdigeBehandlinger") {
            //language=sql
            """
            with sak as (
                select id as sakId, saksnummer
                from sak
            ),
                 behandlinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            b.id,
                            b.status            as status,
                            'SØKNADSBEHANDLING' as type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(b.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet
                     from sak
                              join behandling b on b.sakid = sak.sakId
                     where b.status like ('IVERKSATT%')
                 ),
                 revurderinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            r.id,
                            r.revurderingstype                                         as status,
                            'REVURDERING'                                              as type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(r.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet
                     from sak
                              join revurdering r on r.sakid = sak.sakid
                     where r.revurderingstype like ('IVERKSATT%')
                 ),
                 klage as (
                     select sak.sakId,
                            sak.saksnummer,
                            k.id,
                            k.type                                                     as status,
                            'KLAGE'                                                    as type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(k.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet
                     from sak
                              join klage k on sak.sakId = k.sakid
                     where k.type like ('iverksatt%')
                        or k.type like 'oversendt'
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
            status = hentStatus(behandlingstype),
            behandlingStartet = tidspunkt("iverksattOpprettet"),
        )
    }

    private fun Row.hentStatus(
        behandlingsTypeDB: BehandlingsTypeDB,
    ): Behandlingsoversikt.Behandlingsstatus {
        return when (behandlingsTypeDB) {
            BehandlingsTypeDB.SØKNADSBEHANDLING -> BehandlingsStatus.valueOf(string("status"))
                .tilBehandlingsoversiktStatus()
            BehandlingsTypeDB.REVURDERING -> RevurderingsType.valueOf(string("status"))
                .tilBehandlingsoversiktStatus()
            BehandlingsTypeDB.KLAGE -> KlagePostgresRepo.Tilstand.fromString(string("status"))
                .tilBehandlingsoversiktStatus()
        }
    }

    private fun BehandlingsStatus.tilBehandlingsoversiktStatus(): Behandlingsoversikt.Behandlingsstatus {
        return when (this) {
            BehandlingsStatus.OPPRETTET,
            BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
            BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
            BehandlingsStatus.BEREGNET_INNVILGET,
            BehandlingsStatus.BEREGNET_AVSLAG,
            BehandlingsStatus.SIMULERT,
            BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
            BehandlingsStatus.UNDERKJENT_INNVILGET,
            BehandlingsStatus.UNDERKJENT_AVSLAG,
            -> throw IllegalStateException("Behandlinger som ikke er iverksatt, avslått, eller avsluttet er ikke en ferdig behandling")

            BehandlingsStatus.IVERKSATT_INNVILGET -> Behandlingsoversikt.Behandlingsstatus.INNVILGET

            BehandlingsStatus.IVERKSATT_AVSLAG -> Behandlingsoversikt.Behandlingsstatus.AVSLAG
        }
    }

    private fun RevurderingsType.tilBehandlingsoversiktStatus(): Behandlingsoversikt.Behandlingsstatus {
        return when (this) {
            RevurderingsType.OPPRETTET,
            RevurderingsType.BEREGNET_INNVILGET,
            RevurderingsType.BEREGNET_OPPHØRT,
            RevurderingsType.BEREGNET_INGEN_ENDRING,
            RevurderingsType.SIMULERT_INNVILGET,
            RevurderingsType.SIMULERT_OPPHØRT,
            RevurderingsType.SIMULERT_STANS,
            RevurderingsType.SIMULERT_GJENOPPTAK,
            RevurderingsType.TIL_ATTESTERING_INNVILGET,
            RevurderingsType.TIL_ATTESTERING_OPPHØRT,
            RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING,
            RevurderingsType.UNDERKJENT_INNVILGET,
            RevurderingsType.UNDERKJENT_OPPHØRT,
            RevurderingsType.UNDERKJENT_INGEN_ENDRING,
            -> throw IllegalStateException("Behandlinger som ikke er iverksatt, avslått, eller avsluttet er ikke en ferdig behandling")

            RevurderingsType.IVERKSATT_STANS -> Behandlingsoversikt.Behandlingsstatus.STANS

            RevurderingsType.IVERKSATT_OPPHØRT -> Behandlingsoversikt.Behandlingsstatus.OPPHØR

            RevurderingsType.IVERKSATT_INGEN_ENDRING -> Behandlingsoversikt.Behandlingsstatus.INGEN_ENDRING

            RevurderingsType.IVERKSATT_GJENOPPTAK -> Behandlingsoversikt.Behandlingsstatus.GJENOPPTAK

            RevurderingsType.IVERKSATT_INNVILGET -> Behandlingsoversikt.Behandlingsstatus.INNVILGET
        }
    }

    private fun KlagePostgresRepo.Tilstand.tilBehandlingsoversiktStatus(): Behandlingsoversikt.Behandlingsstatus {
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
            KlagePostgresRepo.Tilstand.TIL_ATTESTERING_TIL_VURDERING,
            KlagePostgresRepo.Tilstand.TIL_ATTESTERING_AVVIST,
            -> throw IllegalStateException("Behandlinger som ikke er iverksatt, avslått, eller avsluttet er ikke en ferdig behandling")

            KlagePostgresRepo.Tilstand.OVERSENDT -> Behandlingsoversikt.Behandlingsstatus.OVERSENDT

            KlagePostgresRepo.Tilstand.IVERKSATT_AVVIST -> Behandlingsoversikt.Behandlingsstatus.AVSLAG
        }
    }

    private enum class BehandlingsTypeDB {
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE;

        fun toBehandlingstype(): Behandlingsoversikt.Behandlingstype {
            return when (this) {
                SØKNADSBEHANDLING -> Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING
                REVURDERING -> Behandlingsoversikt.Behandlingstype.REVURDERING
                KLAGE -> Behandlingsoversikt.Behandlingstype.KLAGE
            }
        }
    }
}
