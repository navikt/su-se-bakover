package no.nav.su.se.bakover.database.statistikk

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
                        hendelse_tid, teknisk_tid, sak_id, saksnummer, behandling_id, relatert_behandling_id,
                        sak_ytelse, sak_utland, behandling_type, behandling_metode, mottatt_tid, registrert_tid,
                        ferdigbehandlet_tid, utbetalt_tid, behandling_status, behandling_resultat, behandling_begrunnelse,
                        behandling_aarsak, opprettet_av, saksbehandler, ansvarlig_beslutter, ansvarlig_enhet,
                        vedtakslosning_navn, funksjonell_periode_fom, funksjonell_periode_tom,
                        tilbakekrev_beloep, aktorid                   
                    ) VALUES (
                        :hendelse_tid, :teknisk_tid, :sak_id, :saksnummer, :behandling_id, :relatert_behandling_id,
                        :sak_ytelse, :sak_utland, :behandling_type, :behandling_metode, :mottatt_tid, :registrert_tid,
                        :ferdigbehandlet_tid, :utbetalt_tid, :behandling_status, :behandling_resultat,
                        :behandling_begrunnelse,:behandling_aarsak, :opprettet_av, :saksbehandler, :ansvarlig_beslutter,
                        :ansvarlig_enhet, :vedtakslosning_navn,:funksjonell_periode_fom, :funksjonell_periode_tom,
                        :tilbakekrev_beloep, :aktorid                   
                    )
                    """.trimIndent()
                        .insert(
                            mapOf(
                                "hendelse_tid" to sakStatistikk.hendelseTid,
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
                                "vedtakslosning_navn" to sakStatistikk.vedtaksløsningNavn,
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
            ) { row ->
                SakStatistikk(
                    hendelseTid = row.tidspunkt("hendelse_tid"),
                    tekniskTid = row.tidspunkt("teknisk_tid"),
                    sakId = row.uuid("sak_id"),
                    saksnummer = row.long("saksnummer"),
                    behandlingId = row.uuid("behandling_id"),
                    relatertBehandlingId = row.uuidOrNull("relatert_behandling_id"),
                    aktorId = Fnr(row.string("aktorid")),
                    sakYtelse = row.string("sak_ytelse"),
                    sakUtland = row.string("sak_utland"),
                    behandlingType = row.string("behandling_type"),
                    behandlingMetode = BehandlingMetode.valueOf(row.string("behandling_metode")),
                    mottattTid = row.tidspunkt("mottatt_tid"),
                    registrertTid = row.tidspunkt("registrert_tid"),
                    ferdigbehandletTid = row.tidspunktOrNull("ferdigbehandlet_tid"),
                    utbetaltTid = row.localDateOrNull("utbetalt_tid"),
                    behandlingStatus = row.string("behandling_status"),
                    behandlingResultat = row.stringOrNull("behandling_resultat"),
                    resultatBegrunnelse = row.stringOrNull("behandling_begrunnelse"),
                    behandlingAarsak = row.stringOrNull("behandling_aarsak"),
                    opprettetAv = row.string("opprettet_av"),
                    saksbehandler = row.stringOrNull("saksbehandler"),
                    ansvarligBeslutter = row.stringOrNull("ansvarlig_beslutter"),
                    ansvarligEnhet = row.string("ansvarlig_enhet"),
                    vedtaksløsningNavn = row.string("vedtakslosning_navn"),
                    funksjonellPeriodeFom = row.localDateOrNull("funksjonell_periode_fom"),
                    funksjonellPeriodeTom = row.localDateOrNull("funksjonell_periode_tom"),
                    tilbakekrevBeløp = row.longOrNull("tilbakekrev_beloep"),
                )
            }
        }
    }
}
