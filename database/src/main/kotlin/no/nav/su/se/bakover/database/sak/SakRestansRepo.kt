package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Restans
import no.nav.su.se.bakover.domain.behandling.RestansStatus
import no.nav.su.se.bakover.domain.behandling.RestansType
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.UUID
import javax.sql.DataSource

internal class SakRestansRepo(
    private val dataSource: DataSource,
    private val dbMetrics: DbMetrics,
) {
    fun hentRestanser(): List<Restans> {
        return dbMetrics.timeQuery("hentRestanser") {
            dataSource.withSession { session ->
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
                     where b.status not like ('IVERKSATT%')
                 ),
                 revurderinger as (
                     select sak.sakId, sak.saksnummer, r.id, r.opprettet, r.revurderingstype as status, 'REVURDERING' as type
                     from sak
                              join behandling_vedtak bv on bv.sakid = sak.sakId
                              join revurdering r on r.vedtaksomrevurderesid = bv.vedtakid
                     where r.revurderingstype not like ('IVERKSATT%')
                 ),
                 søknader as (
                     select sak.sakId, sak.saksnummer, s.id, s.opprettet, 'NY_SØKNAD' as status, 'SØKNAD' as type
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
                 )
            select *
            from slåttSammen
                        """.hentListe(emptyMap(), session) {
                    it.toRestans()
                }
            }
        }
    }

    private fun Row.toRestans(): Restans {
        val restansType = when (RestansTypeDB.valueOf(string("type"))) {
            RestansTypeDB.SØKNAD,
            RestansTypeDB.SØKNADSBEHANDLING,
            -> RestansType.SØKNADSBEHANDLING
            RestansTypeDB.REVURDERING -> RestansType.REVURDERING
        }

        return Restans(
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingsId = UUID.fromString(string("id")),
            restansType = restansType,
            status = when (RestansTypeDB.valueOf(string("type"))) {
                RestansTypeDB.SØKNADSBEHANDLING -> behandlingStatusTilRestansStatus(BehandlingsStatus.valueOf(string("status")))
                RestansTypeDB.REVURDERING -> revurderingTypeTilRestansStatus(RevurderingsType.valueOf(string("status")))
                RestansTypeDB.SØKNAD -> RestansStatus.NY_SØKNAD
            },
            opprettet = tidspunkt("opprettet"),
        )
    }

    private fun behandlingStatusTilRestansStatus(status: BehandlingsStatus): RestansStatus {
        return when (status) {
            BehandlingsStatus.OPPRETTET,
            BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
            BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
            BehandlingsStatus.BEREGNET_INNVILGET,
            BehandlingsStatus.BEREGNET_AVSLAG,
            BehandlingsStatus.SIMULERT,
            -> RestansStatus.UNDER_BEHANDLING

            BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
            -> RestansStatus.TIL_ATTESTERING

            BehandlingsStatus.UNDERKJENT_INNVILGET,
            BehandlingsStatus.UNDERKJENT_AVSLAG,
            -> RestansStatus.UNDERKJENT

            else -> throw IllegalStateException("Iverksatte behandlinger er ikke en restans")
        }
    }

    private fun revurderingTypeTilRestansStatus(type: RevurderingsType): RestansStatus {
        return when (type) {
            RevurderingsType.OPPRETTET,
            RevurderingsType.BEREGNET_INNVILGET,
            RevurderingsType.BEREGNET_OPPHØRT,
            RevurderingsType.BEREGNET_INGEN_ENDRING,
            RevurderingsType.SIMULERT_INNVILGET,
            RevurderingsType.SIMULERT_OPPHØRT,
            -> RestansStatus.UNDER_BEHANDLING

            RevurderingsType.TIL_ATTESTERING_INNVILGET,
            RevurderingsType.TIL_ATTESTERING_OPPHØRT,
            RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING,
            -> RestansStatus.TIL_ATTESTERING

            RevurderingsType.UNDERKJENT_INNVILGET,
            RevurderingsType.UNDERKJENT_OPPHØRT,
            RevurderingsType.UNDERKJENT_INGEN_ENDRING,
            -> RestansStatus.UNDERKJENT

            else -> throw IllegalStateException("Iverksatte behandlinger er ikke en restans.")
        }
    }

    private enum class RestansTypeDB {
        SØKNAD,
        SØKNADSBEHANDLING,
        REVURDERING
    }
}
