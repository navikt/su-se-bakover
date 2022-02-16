package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.Saksnummer
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
                            b.opprettet,
                            b.status            as status,
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
                            r.revurderingstype                                         as status,
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
                            k.type                                                     as status,
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
                it.toBehandlingsoversikt()
            }
        }
    }

    private fun Row.toBehandlingsoversikt(): Behandlingsoversikt {
        val behandlingstype = BehandlingsTypeDB.valueOf(string("type"))

        val erAvsluttet = boolean("avsluttet")

        return Behandlingsoversikt(
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingsId = UUID.fromString(string("id")),
            behandlingstype = behandlingstype.toBehandlingstype(),
            status = hentStatus(behandlingstype, erAvsluttet),
            behandlingStartet = tidspunkt("opprettet"),
        )
    }

    private fun Row.hentStatus(
        behandlingsTypeDB: BehandlingsTypeDB,
        erBehandlingAvsluttet: Boolean,
    ): Behandlingsoversikt.Behandlingsstatus {
        if (erBehandlingAvsluttet) {
            return Behandlingsoversikt.Behandlingsstatus.AVSLUTTET
        }
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

            RevurderingsType.IVERKSATT_STANS -> Behandlingsoversikt.Behandlingsstatus.AVSLAG

            RevurderingsType.IVERKSATT_OPPHØRT -> Behandlingsoversikt.Behandlingsstatus.OPPHØR

            RevurderingsType.IVERKSATT_INGEN_ENDRING -> Behandlingsoversikt.Behandlingsstatus.INGEN_ENDRING

            RevurderingsType.IVERKSATT_GJENOPPTAK,
            RevurderingsType.IVERKSATT_INNVILGET,
            -> Behandlingsoversikt.Behandlingsstatus.INNVILGET
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

            KlagePostgresRepo.Tilstand.OVERSENDT ->
                Behandlingsoversikt.Behandlingsstatus.INNVILGET
            KlagePostgresRepo.Tilstand.IVERKSATT_AVVIST ->
                Behandlingsoversikt.Behandlingsstatus.AVSLAG
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
