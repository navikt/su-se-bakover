package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPosgresRepo
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Restans
import no.nav.su.se.bakover.domain.behandling.RestansStatus
import no.nav.su.se.bakover.domain.behandling.RestansType
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.UUID
import javax.sql.DataSource

internal class SakPostgresRepo(
    private val dataSource: DataSource,
    private val søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val revurderingRepo: RevurderingPostgresRepo,
    private val vedtakPosgresRepo: VedtakPosgresRepo,
) : SakRepo {
    override fun hentSak(sakId: UUID) = dataSource.withSession { hentSakInternal(sakId, it) }
    override fun hentSak(fnr: Fnr) = dataSource.withSession { hentSakInternal(fnr, it) }
    override fun hentSak(saksnummer: Saksnummer) = dataSource.withSession { hentSakInternal(saksnummer, it) }

    private enum class RestansTypeDB {
        SØKNAD,
        SØKNADSBEHANDLING,
        REVURDERING
    }

    override fun opprettSak(sak: NySak) {
        dataSource.withSession { session ->
            """
            with inserted_sak as (insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            insert into søknad (id, sakId, søknadInnhold, opprettet) values (:soknadId, :sakId, to_json(:soknad::json), :opprettet)
        """.insert(
                mapOf(
                    "sakId" to sak.id,
                    "fnr" to sak.fnr,
                    "opprettet" to sak.opprettet,
                    "soknadId" to sak.søknad.id,
                    "soknad" to objectMapper.writeValueAsString(sak.søknad.søknadInnhold),
                ),
                session,
            )
        }
    }

    override fun hentRestanser(): List<Restans> {
        return dataSource.withSession { session ->
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
                it.toUttrekk()
            }
        }
    }

    private fun hentSakInternal(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
        .hent(mapOf("fnr" to fnr.toString()), session) { it.toSak(session) }

    private fun hentSakInternal(sakId: UUID, session: Session): Sak? = "select * from sak where id=:sakId"
        .hent(mapOf("sakId" to sakId), session) { it.toSak(session) }

    private fun hentSakInternal(saksnummer: Saksnummer, session: Session): Sak? =
        "select * from sak where saksnummer=:saksnummer"
            .hent(mapOf("saksnummer" to saksnummer.nummer), session) { it.toSak(session) }

    private fun Row.toUttrekk(): Restans {

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

    private fun Row.toSak(session: Session): Sak {
        val sakId = UUID.fromString(string("id"))
        return Sak(
            id = sakId,
            saksnummer = Saksnummer(long("saksnummer")),
            fnr = Fnr(string("fnr")),
            opprettet = tidspunkt("opprettet"),
            søknader = SøknadRepoInternal.hentSøknaderInternal(sakId, session),
            behandlinger = søknadsbehandlingRepo.hentForSak(sakId, session),
            utbetalinger = UtbetalingInternalRepo.hentUtbetalinger(sakId, session),
            revurderinger = revurderingRepo.hentRevurderingerForSak(sakId, session),
            vedtakListe = vedtakPosgresRepo.hentForSakId(sakId, session),
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
            else -> throw IllegalStateException("Fikk en ugyldig status for å mappe")
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

            else -> throw IllegalStateException("Fikk en ugyldig type for å mappe")
        }
    }
}
