package no.nav.su.se.bakover.database.statistikk

import kotliquery.Row
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.statistikk.BehandlingMetode
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import java.time.LocalDate
import java.util.UUID

class SakStatistikkRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : SakStatistikkRepo {
    override fun lagreSakStatistikk(sakStatistikk: SakStatistikk, sessionContext: SessionContext?) {
        return dbMetrics.timeQuery("lagreSakStatistikk") {
            sessionFactory.withSessionContext(sessionContext) { sessionContext ->
                sessionContext.withSession { session ->
                    """
                    INSERT INTO sak_statistikk (
                        funksjonell_tid, teknisk_tid, sak_id, saksnummer, behandling_id, relatert_behandling_id,
                        sak_ytelse, sak_utland, behandling_type, behandling_metode, mottatt_tid, registrert_tid,
                        ferdigbehandlet_tid, utbetalt_tid, behandling_status, behandling_resultat, behandling_begrunnelse,
                        behandling_aarsak, opprettet_av, saksbehandler, ansvarlig_beslutter, ansvarlig_enhet,
                        funksjonell_periode_fom, funksjonell_periode_tom,
                        tilbakekrev_beloep, aktorid                   
                    ) VALUES (
                        :funksjonell_tid, :teknisk_tid, :sak_id, :saksnummer, :behandling_id, :relatert_behandling_id,
                        :sak_ytelse, :sak_utland, :behandling_type, :behandling_metode, :mottatt_tid, :registrert_tid,
                        :ferdigbehandlet_tid, :utbetalt_tid, :behandling_status, :behandling_resultat,
                        :behandling_begrunnelse,:behandling_aarsak, :opprettet_av, :saksbehandler, :ansvarlig_beslutter,
                        :ansvarlig_enhet, :funksjonell_periode_fom, :funksjonell_periode_tom,
                        :tilbakekrev_beloep, :aktorid                   
                    )
                    """.trimIndent()
                        .insert(
                            mapOf(
                                "funksjonell_tid" to sakStatistikk.funksjonellTid,
                                "teknisk_tid" to sakStatistikk.tekniskTid,
                                "sak_id" to sakStatistikk.sakId,
                                "saksnummer" to sakStatistikk.saksnummer,
                                "behandling_id" to sakStatistikk.behandlingId,
                                "relatert_behandling_id" to sakStatistikk.relatertBehandlingId,
                                "aktorid" to sakStatistikk.aktorId,
                                "sak_ytelse" to sakStatistikk.sakYtelse,
                                "sak_utland" to sakStatistikk.sakUtland,
                                "behandling_type" to sakStatistikk.behandlingType,
                                "behandling_metode" to sakStatistikk.behandlingMetode.name,
                                "mottatt_tid" to sakStatistikk.mottattTid,
                                "registrert_tid" to sakStatistikk.registrertTid,
                                "ferdigbehandlet_tid" to sakStatistikk.ferdigbehandletTid,
                                "utbetalt_tid" to sakStatistikk.utbetaltTid,
                                "behandling_status" to sakStatistikk.behandlingStatus,
                                "behandling_resultat" to sakStatistikk.behandlingResultat,
                                "behandling_begrunnelse" to sakStatistikk.resultatBegrunnelse,
                                "behandling_aarsak" to sakStatistikk.behandlingAarsak,
                                "opprettet_av" to sakStatistikk.opprettetAv,
                                "saksbehandler" to sakStatistikk.saksbehandler,
                                "ansvarlig_beslutter" to sakStatistikk.ansvarligBeslutter,
                                "ansvarlig_enhet" to sakStatistikk.ansvarligEnhet,
                                "funksjonell_periode_fom" to sakStatistikk.funksjonellPeriodeFom,
                                "funksjonell_periode_tom" to sakStatistikk.funksjonellPeriodeTom,
                                "tilbakekrev_beloep" to sakStatistikk.tilbakekrevBeløp,
                            ),
                            session = session,
                        )
                }
            }
        }
    }

    override fun hentSakStatistikk(sakId: UUID): List<SakStatistikk> {
        return sessionFactory.withSession { session ->
            """
                SELECT * FROM sak_statistikk
                WHERE sak_id = :sak_id
            """.trimIndent().hentListe(
                params = mapOf("sak_id" to sakId),
                session = session,
            ) { it.toSakStatistikk() }
        }
    }

    override fun hentSakStatistikk(fom: LocalDate, tom: LocalDate): List<SakStatistikk> {
        return sessionFactory.withSession { session ->
            """
                SELECT * FROM sak_statistikk
                WHERE funksjonell_tid > :fom and funksjonell_tid < :tom 
            """.trimIndent().hentListe(
                params = mapOf(
                    "fom" to fom,
                    "tom" to tom,
                ),
                session = session,
            ) { it.toSakStatistikk() }
        }
    }

    override fun hentInitiellBehandlingsstatistikk(
        behandlingsid: BehandlingsId,
        sessionContext: SessionContext?,
    ): SakStatistikk? {
        val hendelserForBehandling = sessionFactory.withSessionContext(sessionContext) { sessionContext ->
            sessionContext.withSession { session ->
                """
                SELECT * FROM sak_statistikk
                WHERE behandling_id = :behandling_id
                """.trimIndent().hentListe(
                    params = mapOf("behandling_id" to behandlingsid.value),
                    session = session,
                ) { it.toSakStatistikk() }
            }
        }
        return hendelserForBehandling.minByOrNull { it.funksjonellTid }
    }

    private fun Row.toSakStatistikk() = SakStatistikk(
        funksjonellTid = tidspunkt("funksjonell_tid"),
        tekniskTid = tidspunkt("teknisk_tid"),
        sakId = uuid("sak_id"),
        saksnummer = long("saksnummer"),
        behandlingId = uuid("behandling_id"),
        relatertBehandlingId = uuidOrNull("relatert_behandling_id"),
        aktorId = Fnr(string("aktorid")),
        sakYtelse = string("sak_ytelse"),
        sakUtland = string("sak_utland"),
        behandlingType = string("behandling_type"),
        behandlingMetode = BehandlingMetode.valueOf(string("behandling_metode")),
        mottattTid = tidspunkt("mottatt_tid"),
        registrertTid = tidspunkt("registrert_tid"),
        ferdigbehandletTid = tidspunktOrNull("ferdigbehandlet_tid"),
        utbetaltTid = localDateOrNull("utbetalt_tid"),
        behandlingStatus = string("behandling_status"),
        behandlingResultat = stringOrNull("behandling_resultat"),
        resultatBegrunnelse = stringOrNull("behandling_begrunnelse"),
        behandlingAarsak = stringOrNull("behandling_aarsak"),
        opprettetAv = stringOrNull("opprettet_av"),
        saksbehandler = stringOrNull("saksbehandler"),
        ansvarligBeslutter = stringOrNull("ansvarlig_beslutter"),
        ansvarligEnhet = string("ansvarlig_enhet"),
        funksjonellPeriodeFom = localDateOrNull("funksjonell_periode_fom"),
        funksjonellPeriodeTom = localDateOrNull("funksjonell_periode_tom"),
        tilbakekrevBeløp = longOrNull("tilbakekrev_beloep"),
        sekvensId = int("id_sekvens").toBigInteger(),
    )
}
