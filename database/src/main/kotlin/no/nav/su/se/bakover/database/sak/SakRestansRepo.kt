package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.tidspunktOrNull
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.SakRestans
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.UUID

internal class SakRestansRepo(
    private val dbMetrics: DbMetrics,
) {
    /**
     * Henter åpne søknadsbehandlinger, åpne revurderinger, og nye søknader
     */
    fun hentSakRestanser(session: Session): List<SakRestans> {
        return dbMetrics.timeQuery("hentSakRestanser") {
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
                     where r.revurderingstype not like ('IVERKSATT%')
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
                 )
            select *
            from slåttSammen
                        """.hentListe(emptyMap(), session) {
                it.toRestans()
            }
        }
    }

    private fun Row.toRestans(): SakRestans {
        val restansType = when (RestansTypeDB.valueOf(string("type"))) {
            RestansTypeDB.SØKNAD,
            RestansTypeDB.SØKNADSBEHANDLING,
            -> SakRestans.RestansType.SØKNADSBEHANDLING
            RestansTypeDB.REVURDERING,
            -> SakRestans.RestansType.REVURDERING
        }

        return SakRestans(
            saksnummer = Saksnummer(long("saksnummer")),
            behandlingsId = UUID.fromString(string("id")),
            restansType = restansType,
            status = when (RestansTypeDB.valueOf(string("type"))) {
                RestansTypeDB.SØKNADSBEHANDLING -> behandlingStatusTilRestansStatus(BehandlingsStatus.valueOf(string("status")))
                RestansTypeDB.REVURDERING -> revurderingTypeTilRestansStatus(RevurderingsType.valueOf(string("status")))
                RestansTypeDB.SØKNAD -> SakRestans.RestansStatus.NY_SØKNAD
            },
            behandlingStartet = tidspunktOrNull("opprettet"),
        )
    }

    private fun behandlingStatusTilRestansStatus(status: BehandlingsStatus): SakRestans.RestansStatus {
        return when (status) {
            BehandlingsStatus.OPPRETTET,
            BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
            BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
            BehandlingsStatus.BEREGNET_INNVILGET,
            BehandlingsStatus.BEREGNET_AVSLAG,
            BehandlingsStatus.SIMULERT,
            -> SakRestans.RestansStatus.UNDER_BEHANDLING

            BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
            -> SakRestans.RestansStatus.TIL_ATTESTERING

            BehandlingsStatus.UNDERKJENT_INNVILGET,
            BehandlingsStatus.UNDERKJENT_AVSLAG,
            -> SakRestans.RestansStatus.UNDERKJENT

            else -> throw IllegalStateException("Iverksatte behandlinger er ikke en restans")
        }
    }

    private fun revurderingTypeTilRestansStatus(type: RevurderingsType): SakRestans.RestansStatus {
        return when (type) {
            RevurderingsType.OPPRETTET,
            RevurderingsType.BEREGNET_INNVILGET,
            RevurderingsType.BEREGNET_OPPHØRT,
            RevurderingsType.BEREGNET_INGEN_ENDRING,
            RevurderingsType.SIMULERT_INNVILGET,
            RevurderingsType.SIMULERT_OPPHØRT,
            -> SakRestans.RestansStatus.UNDER_BEHANDLING

            RevurderingsType.TIL_ATTESTERING_INNVILGET,
            RevurderingsType.TIL_ATTESTERING_OPPHØRT,
            RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING,
            RevurderingsType.SIMULERT_STANS,
            RevurderingsType.SIMULERT_GJENOPPTAK,
            -> SakRestans.RestansStatus.TIL_ATTESTERING

            RevurderingsType.UNDERKJENT_INNVILGET,
            RevurderingsType.UNDERKJENT_OPPHØRT,
            RevurderingsType.UNDERKJENT_INGEN_ENDRING,
            -> SakRestans.RestansStatus.UNDERKJENT

            RevurderingsType.IVERKSATT_INNVILGET,
            RevurderingsType.IVERKSATT_INGEN_ENDRING,
            RevurderingsType.IVERKSATT_OPPHØRT,
            RevurderingsType.IVERKSATT_STANS,
            RevurderingsType.IVERKSATT_GJENOPPTAK,
            -> throw IllegalStateException("Iverksatte behandlinger er ikke en restans.")
        }
    }

    private enum class RestansTypeDB {
        SØKNAD,
        SØKNADSBEHANDLING,
        REVURDERING
    }
}
