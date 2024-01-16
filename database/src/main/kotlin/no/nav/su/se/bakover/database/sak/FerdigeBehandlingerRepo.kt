package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingStatusDB
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.kravgrunnlag.repo.BehandlingssammendragKravgrunnlagRepo

internal class FerdigeBehandlingerRepo(
    private val dbMetrics: DbMetrics,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val behandlingssammendragKravgrunnlagRepo: BehandlingssammendragKravgrunnlagRepo,
    private val sessionFactory: SessionFactory,
) {

    /**
     * Innvilget, avslått, opphørt og avsluttede/lukkede behandlinger.
     */
    fun hentFerdigeBehandlinger(sessionContext: SessionContext? = null): List<Behandlingssammendrag> {
        return sessionContext.withOptionalSession(sessionFactory) {
            hentFerdigeBehandlingerUtenTilbakekreving(sessionContext).plus(
                tilbakekrevingsbehandlingRepo.hentFerdigeBehandlingssamendrag(sessionContext),
            ).plus(
                behandlingssammendragKravgrunnlagRepo.hentBehandlingssammendrag(sessionContext),
            )
        }
    }

    /**
     * Innvilget, avslått, opphørt og avsluttede/lukkede behandlinger.
     */
    private fun hentFerdigeBehandlingerUtenTilbakekreving(sessionContext: SessionContext? = null): List<Behandlingssammendrag> {
        return dbMetrics.timeQuery("hentFerdigeBehandlinger") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                //language=sql
                """
            with sak as (
                select id as sakId, saksnummer
                from sak
            ),
                 behandlinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            b.status            as status,
                            'SØKNADSBEHANDLING' as type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(b.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet,
                            (stønadsperiode ->> 'periode')::jsonb as periode
                     from sak
                              join behandling b on b.sakid = sak.sakId
                     where b.status like ('IVERKSATT%')
                 ),
                 revurderinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            r.revurderingstype                                         as status,
                            'REVURDERING'                                              as type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(r.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet,
                            (r.periode)::jsonb as periode
                     from sak
                              join revurdering r on r.sakid = sak.sakid
                     where r.revurderingstype like ('IVERKSATT%')
                 ),
                 klage as (
                     select sak.sakId,
                            sak.saksnummer,
                            k.type                                                     as status,
                            'KLAGE'                                                    as type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(k.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet,
                            null::jsonb as periode
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
    }

    private fun Row.toBehandlingsoversikt(): Behandlingssammendrag {
        val behandlingstype = BehandlingsTypeDB.valueOf(string("type"))

        return Behandlingssammendrag(
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingstype = behandlingstype.toBehandlingstype(),
            status = hentStatus(behandlingstype),
            behandlingStartet = tidspunkt("iverksattOpprettet"),
            periode = stringOrNull("periode")?.let { deserialize<Periode>(it) },
        )
    }

    private fun Row.hentStatus(
        behandlingsTypeDB: BehandlingsTypeDB,
    ): Behandlingssammendrag.Behandlingsstatus {
        return when (behandlingsTypeDB) {
            BehandlingsTypeDB.SØKNADSBEHANDLING -> SøknadsbehandlingStatusDB.valueOf(string("status"))
                .tilBehandlingsoversiktStatus()

            BehandlingsTypeDB.REVURDERING -> RevurderingsType.valueOf(string("status"))
                .tilBehandlingsoversiktStatus()

            BehandlingsTypeDB.KLAGE -> KlagePostgresRepo.Tilstand.fromString(string("status"))
                .tilBehandlingsoversiktStatus()
        }
    }

    private fun SøknadsbehandlingStatusDB.tilBehandlingsoversiktStatus(): Behandlingssammendrag.Behandlingsstatus {
        return when (this) {
            SøknadsbehandlingStatusDB.OPPRETTET,
            SøknadsbehandlingStatusDB.VILKÅRSVURDERT_INNVILGET,
            SøknadsbehandlingStatusDB.VILKÅRSVURDERT_AVSLAG,
            SøknadsbehandlingStatusDB.BEREGNET_INNVILGET,
            SøknadsbehandlingStatusDB.BEREGNET_AVSLAG,
            SøknadsbehandlingStatusDB.SIMULERT,
            SøknadsbehandlingStatusDB.TIL_ATTESTERING_INNVILGET,
            SøknadsbehandlingStatusDB.TIL_ATTESTERING_AVSLAG,
            SøknadsbehandlingStatusDB.UNDERKJENT_INNVILGET,
            SøknadsbehandlingStatusDB.UNDERKJENT_AVSLAG,
            -> throw IllegalStateException("Behandlinger som ikke er iverksatt, avslått, eller avsluttet er ikke en ferdig behandling")

            SøknadsbehandlingStatusDB.IVERKSATT_INNVILGET -> Behandlingssammendrag.Behandlingsstatus.INNVILGET

            SøknadsbehandlingStatusDB.IVERKSATT_AVSLAG -> Behandlingssammendrag.Behandlingsstatus.AVSLAG
        }
    }

    private fun RevurderingsType.tilBehandlingsoversiktStatus(): Behandlingssammendrag.Behandlingsstatus {
        return when (this) {
            RevurderingsType.OPPRETTET,
            RevurderingsType.BEREGNET_INNVILGET,
            RevurderingsType.BEREGNET_OPPHØRT,
            RevurderingsType.SIMULERT_INNVILGET,
            RevurderingsType.SIMULERT_OPPHØRT,
            RevurderingsType.SIMULERT_STANS,
            RevurderingsType.SIMULERT_GJENOPPTAK,
            RevurderingsType.TIL_ATTESTERING_INNVILGET,
            RevurderingsType.TIL_ATTESTERING_OPPHØRT,
            RevurderingsType.UNDERKJENT_INNVILGET,
            RevurderingsType.UNDERKJENT_OPPHØRT,
            -> throw IllegalStateException("Behandlinger som ikke er iverksatt, avslått, eller avsluttet er ikke en ferdig behandling")

            RevurderingsType.IVERKSATT_STANS -> Behandlingssammendrag.Behandlingsstatus.STANS

            RevurderingsType.IVERKSATT_OPPHØRT -> Behandlingssammendrag.Behandlingsstatus.OPPHØR

            RevurderingsType.IVERKSATT_GJENOPPTAK -> Behandlingssammendrag.Behandlingsstatus.GJENOPPTAK

            RevurderingsType.IVERKSATT_INNVILGET -> Behandlingssammendrag.Behandlingsstatus.INNVILGET
        }
    }

    private fun KlagePostgresRepo.Tilstand.tilBehandlingsoversiktStatus(): Behandlingssammendrag.Behandlingsstatus {
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

            KlagePostgresRepo.Tilstand.OVERSENDT -> Behandlingssammendrag.Behandlingsstatus.OVERSENDT

            KlagePostgresRepo.Tilstand.IVERKSATT_AVVIST -> Behandlingssammendrag.Behandlingsstatus.AVSLAG
        }
    }

    private enum class BehandlingsTypeDB {
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE,
        ;

        fun toBehandlingstype(): Behandlingssammendrag.Behandlingstype {
            return when (this) {
                SØKNADSBEHANDLING -> Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING
                REVURDERING -> Behandlingssammendrag.Behandlingstype.REVURDERING
                KLAGE -> Behandlingssammendrag.Behandlingstype.KLAGE
            }
        }
    }
}
