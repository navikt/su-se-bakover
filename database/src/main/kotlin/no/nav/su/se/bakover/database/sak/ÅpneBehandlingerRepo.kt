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
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.infrastructure.persistence.toDomain
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingStatusDB
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.repo.BehandlingssammendragKravgrunnlagOgTilbakekrevingRepo

internal class ÅpneBehandlingerRepo(
    private val dbMetrics: DbMetrics,
    private val behandlingssammendragKravgrunnlagOgTilbakekrevingRepo: BehandlingssammendragKravgrunnlagOgTilbakekrevingRepo,
    private val sessionFactory: SessionFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun hentÅpneBehandlinger(sessionContext: SessionContext? = null): List<Behandlingssammendrag> {
        return sessionContext.withOptionalSession(sessionFactory) {
            åpneBehandlingerUtenTilbakekreving(sessionContext).plus(
                behandlingssammendragKravgrunnlagOgTilbakekrevingRepo.hentÅpne(sessionContext),
            )
        }
    }

    /**
     * Henter åpne søknadsbehandlinger, åpne revurderinger, åpne klager, og nye søknader
     */
    private fun åpneBehandlingerUtenTilbakekreving(sessionContext: SessionContext? = null): List<Behandlingssammendrag> {
        val omgjøringsÅrsaker = Revurderingsårsak.Årsak.hentOmgjøringsEnumer()

        return dbMetrics.timeQuery("hentÅpneBehandlinger") {
            sessionContext.withOptionalSession(sessionFactory) {
                //language=postgresql
                """
                with sak as (
                select id as sakId, saksnummer, type as sakType
                from sak
            ),
                 behandlinger as (
                     select sak.sakId, sak.saksnummer, sak.sakType, b.opprettet, b.status as status, 
                     CASE 
                            WHEN b.omgjoringsaarsak IS NOT NULL THEN 'OMGJØRING'
                            ELSE 'SØKNADSBEHANDLING'
                    END AS type,
                    (stønadsperiode ->> 'periode')::jsonb as periode
                     from sak
                              join behandling b on b.sakid = sak.sakId
                     where b.status not like ('IVERKSATT%') and b.lukket = false
                 ),
                 revurderinger as (
                     select sak.sakId, sak.saksnummer, sak.sakType, r.opprettet, r.revurderingstype as status, 
                     CASE
                        WHEN r.årsak IN ($omgjøringsÅrsaker) THEN 'REVURDERING_OMGJØRING' 
                        ELSE  'REVURDERING'                                              
                      END AS type,
                      (r.periode)::jsonb as periode
                     from sak
                              join revurdering r on r.sakid = sak.sakId
                     where r.revurderingstype not like ('IVERKSATT%') and r.avsluttet is null
                 ),
                 klage as (
                     select sak.sakId, sak.saksnummer, sak.sakType, k.opprettet, k.type as status, 'KLAGE' as type, null::jsonb as periode
                     from sak
                              join klage k on sak.sakId = k.sakid
                     where k.type not like ('iverksatt%') and k.type not like 'oversendt' and k.avsluttet is null and k.type != 'omgjort'
                 ),
                 søknader as (
                     select
                        sak.sakId,
                        sak.saksnummer,
                        sak.sakType,
                        null::timestamp as opprettet,
                        'NY_SØKNAD' as status,
                        'SØKNAD' as type,
                        null::jsonb as periode
                     from sak
                              join søknad s on sak.sakId = s.sakid
                     where s.lukket is null
                       and not exists(select 1 from behandling where søknadid = s.id)
                 ),
                 slåttSammen as (
                     select *
                     from søknader
                     union all
                     select *
                     from behandlinger
                     union all
                     select *
                     from revurderinger
                     union all
                     select *
                     from klage
                 )
            select saksnummer, sakType, opprettet, status, type, periode
            from slåttSammen
            """.hentListe(emptyMap(), it) {
                    runCatching { it.toBehandlingsoversikt() }
                        .onFailure { feil -> log.error("Klarte ikke mappe felt for rad: ${feil.message}", feil) }
                        .getOrNull()
                }.filterNotNull()
            }
        }
    }

    private fun Row.toBehandlingsoversikt(): Behandlingssammendrag {
        val behandlingstype = BehandlingsTypeDB.valueOf(string("type"))

        return Behandlingssammendrag(
            sakType = Sakstype.from(string("sakType")),
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingstype = behandlingstype.toBehandlingstype(),
            status = hentÅpenBehandlingStatus(behandlingstype),
            behandlingStartet = tidspunktOrNull("opprettet"),
            periode = stringOrNull("periode")?.let { deserialize<PeriodeDbJson>(it) }?.toDomain(),
        )
    }

    private fun Row.hentÅpenBehandlingStatus(
        behandlingsTypeDB: BehandlingsTypeDB,
    ): Behandlingssammendrag.Behandlingsstatus {
        return when (behandlingsTypeDB) {
            BehandlingsTypeDB.SØKNAD -> Behandlingssammendrag.Behandlingsstatus.NY_SØKNAD
            BehandlingsTypeDB.SØKNADSBEHANDLING, BehandlingsTypeDB.OMGJØRING -> SøknadsbehandlingStatusDB.valueOf(string("status"))
                .tilBehandlingsstatus()

            BehandlingsTypeDB.REVURDERING, BehandlingsTypeDB.REVURDERING_OMGJØRING -> RevurderingsType.valueOf(string("status")).tilBehandlingsstatus()
            BehandlingsTypeDB.KLAGE -> KlagePostgresRepo.Tilstand.fromString(string("status")).tilBehandlingsstatus()
        }
    }
}

private fun SøknadsbehandlingStatusDB.tilBehandlingsstatus(): Behandlingssammendrag.Behandlingsstatus {
    return when (this) {
        SøknadsbehandlingStatusDB.OPPRETTET,
        SøknadsbehandlingStatusDB.VILKÅRSVURDERT_INNVILGET,
        SøknadsbehandlingStatusDB.VILKÅRSVURDERT_AVSLAG,
        SøknadsbehandlingStatusDB.BEREGNET_INNVILGET,
        SøknadsbehandlingStatusDB.BEREGNET_AVSLAG,
        SøknadsbehandlingStatusDB.SIMULERT,
        -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING

        SøknadsbehandlingStatusDB.TIL_ATTESTERING_INNVILGET,
        SøknadsbehandlingStatusDB.TIL_ATTESTERING_AVSLAG,
        -> Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING

        SøknadsbehandlingStatusDB.UNDERKJENT_INNVILGET,
        SøknadsbehandlingStatusDB.UNDERKJENT_AVSLAG,
        -> Behandlingssammendrag.Behandlingsstatus.UNDERKJENT

        SøknadsbehandlingStatusDB.IVERKSATT_INNVILGET,
        SøknadsbehandlingStatusDB.IVERKSATT_AVSLAG,
        -> throw IllegalStateException("Iverksatte søknadsbehandlinger er ikke en åpen behandling")
    }
}

private fun RevurderingsType.tilBehandlingsstatus(): Behandlingssammendrag.Behandlingsstatus {
    return when (this) {
        RevurderingsType.OPPRETTET,
        RevurderingsType.BEREGNET_INNVILGET,
        RevurderingsType.BEREGNET_OPPHØRT,
        RevurderingsType.SIMULERT_INNVILGET,
        RevurderingsType.SIMULERT_OPPHØRT,
        -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING

        RevurderingsType.SIMULERT_STANS,
        RevurderingsType.SIMULERT_GJENOPPTAK,
        RevurderingsType.TIL_ATTESTERING_INNVILGET,
        RevurderingsType.TIL_ATTESTERING_OPPHØRT,
        -> Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING

        RevurderingsType.UNDERKJENT_INNVILGET,
        RevurderingsType.UNDERKJENT_OPPHØRT,
        -> Behandlingssammendrag.Behandlingsstatus.UNDERKJENT

        RevurderingsType.IVERKSATT_STANS,
        RevurderingsType.IVERKSATT_GJENOPPTAK,
        RevurderingsType.IVERKSATT_INNVILGET,
        RevurderingsType.IVERKSATT_OPPHØRT,
        -> throw IllegalStateException("Iverksatte revurderinger er ikke en åpen behandling")
    }
}

private fun KlagePostgresRepo.Tilstand.tilBehandlingsstatus(): Behandlingssammendrag.Behandlingsstatus {
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
        -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING

        KlagePostgresRepo.Tilstand.TIL_ATTESTERING_TIL_VURDERING,
        KlagePostgresRepo.Tilstand.TIL_ATTESTERING_AVVIST,
        -> Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING

        KlagePostgresRepo.Tilstand.OVERSENDT,
        KlagePostgresRepo.Tilstand.IVERKSATT_AVVIST,
        KlagePostgresRepo.Tilstand.AVSLUTTET,
        KlagePostgresRepo.Tilstand.OMGJORT,
        -> throw IllegalStateException("Iverksatte/Oversendte/avsluttede/omgjøringferdigsilt klager er ikke en åpen behandling")
    }
}

private enum class BehandlingsTypeDB {
    SØKNAD,
    OMGJØRING, // På avslag
    SØKNADSBEHANDLING,
    REVURDERING,
    KLAGE,
    REVURDERING_OMGJØRING,
    ;

    fun toBehandlingstype(): Behandlingssammendrag.Behandlingstype {
        return when (this) {
            SØKNAD -> Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING
            SØKNADSBEHANDLING -> Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING
            REVURDERING -> Behandlingssammendrag.Behandlingstype.REVURDERING
            KLAGE -> Behandlingssammendrag.Behandlingstype.KLAGE
            OMGJØRING -> Behandlingssammendrag.Behandlingstype.OMGJØRING
            REVURDERING_OMGJØRING -> Behandlingssammendrag.Behandlingstype.REVURDERING_OMGJØRING
        }
    }
}
