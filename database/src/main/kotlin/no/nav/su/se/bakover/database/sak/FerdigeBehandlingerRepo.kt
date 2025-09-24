package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PeriodeDbJson
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.toDomain
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingStatusDB
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import tilbakekreving.domain.kravgrunnlag.repo.BehandlingssammendragKravgrunnlagOgTilbakekrevingRepo

internal class FerdigeBehandlingerRepo(
    private val dbMetrics: DbMetrics,
    private val behandlingssammendragKravgrunnlagOgTilbakekrevingRepo: BehandlingssammendragKravgrunnlagOgTilbakekrevingRepo,
    private val sessionFactory: SessionFactory,
) {

    /**
     * Innvilget, avslått, opphørt og avsluttede/lukkede behandlinger.
     */
    fun hentFerdigeBehandlinger(sessionContext: SessionContext? = null): List<Behandlingssammendrag> {
        return sessionContext.withOptionalSession(sessionFactory) {
            hentFerdigeBehandlingerUtenTilbakekreving(sessionContext).plus(
                behandlingssammendragKravgrunnlagOgTilbakekrevingRepo.hentFerdige(sessionContext),
            )
        }
    }

    /**
     * Innvilget, avslått, opphørt og avsluttede/lukkede behandlinger.
     */
    private fun hentFerdigeBehandlingerUtenTilbakekreving(sessionContext: SessionContext? = null): List<Behandlingssammendrag> {
        val omgjøringsÅrsaker = Revurderingsårsak.Årsak.hentOmgjøringsEnumer()
        return dbMetrics.timeQuery("hentFerdigeBehandlinger") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                //language=sql
                """
            with sak as (
                select id as sakId, saksnummer, type as sakType
                from sak
            ),
                 behandlinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            sak.sakType,
                            b.status as status,
                            CASE
                                WHEN b.omgjoringsaarsak IS NOT NULL THEN 'OMGJØRING'
                                ELSE 'SØKNADSBEHANDLING'
                            END AS type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(b.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet,
                            (stønadsperiode ->> 'periode')::jsonb as periode
                     from sak
                              join behandling b on b.sakid = sak.sakId
                     where b.status like ('IVERKSATT%')
                 ),
                 revurderinger as (
                     select sak.sakId,
                            sak.saksnummer,
                            sak.sakType,
                            r.revurderingstype                                         as status,
                            CASE
                                WHEN r.årsak IN ($omgjøringsÅrsaker) THEN 'REVURDERING_OMGJØRING' 
                                ELSE  'REVURDERING'                                              
                            END AS type,
                            (select (obj->>'opprettet')::timestamptz from jsonb_array_elements(r.attestering) obj where obj->>'type' = 'Iverksatt') as iverksattOpprettet,
                            (r.periode)::jsonb as periode
                     from sak
                              join revurdering r on r.sakid = sak.sakid
                     where r.revurderingstype like ('IVERKSATT%')
                 ),
                 klage as (
                     select sak.sakId,
                            sak.saksnummer,
                            sak.sakType,
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
            sakType = Sakstype.from(string("sakType")),
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingstype = behandlingstype.toBehandlingstype(),
            status = hentStatus(behandlingstype),
            behandlingStartet = tidspunkt("iverksattOpprettet"),
            periode = stringOrNull("periode")?.let { deserialize<PeriodeDbJson>(it) }?.toDomain(),
        )
    }

    private fun Row.hentStatus(
        behandlingsTypeDB: BehandlingsTypeDB,
    ): Behandlingssammendrag.Behandlingsstatus {
        return when (behandlingsTypeDB) {
            BehandlingsTypeDB.SØKNADSBEHANDLING, BehandlingsTypeDB.OMGJØRING -> SøknadsbehandlingStatusDB.valueOf(string("status"))
                .tilBehandlingsoversiktStatus()

            BehandlingsTypeDB.REVURDERING, BehandlingsTypeDB.REVURDERING_OMGJØRING -> RevurderingsType.valueOf(string("status"))
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
            KlagePostgresRepo.Tilstand.OMGJORT -> Behandlingssammendrag.Behandlingsstatus.IVERKSATT
        }
    }

    private enum class BehandlingsTypeDB {
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE,
        OMGJØRING, // På avslag
        REVURDERING_OMGJØRING,
        ;

        fun toBehandlingstype(): Behandlingssammendrag.Behandlingstype {
            return when (this) {
                SØKNADSBEHANDLING -> Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING
                REVURDERING -> Behandlingssammendrag.Behandlingstype.REVURDERING
                KLAGE -> Behandlingssammendrag.Behandlingstype.KLAGE
                OMGJØRING -> Behandlingssammendrag.Behandlingstype.OMGJØRING
                REVURDERING_OMGJØRING -> Behandlingssammendrag.Behandlingstype.REVURDERING_OMGJØRING
            }
        }
    }
}
