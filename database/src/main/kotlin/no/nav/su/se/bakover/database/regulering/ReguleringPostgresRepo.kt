package no.nav.su.se.bakover.database.regulering

import kotliquery.Row
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.vedtak.VedtakType
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.AutomatiskEllerManuellSak
import no.nav.su.se.bakover.domain.regulering.BehandlingType
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import java.time.LocalDate
import javax.sql.DataSource

internal class ReguleringPostgresRepo(
    private val dataSource: DataSource,
    private val sessionFactory: PostgresSessionFactory,
) : ReguleringRepo {
    override fun hentVedtakSomKanReguleres(dato: LocalDate): List<AutomatiskEllerManuellSak> {
        return dataSource.withSession { session ->
            """
                with sakogid (sakid, saksnummer, bid, fraOgMed, tilOgMed, vedtaktype, opprettet ) as (
                    select bv.sakid
                         , s.saksnummer
                         , coalesce(bv.søknadsbehandlingid, bv.revurderingid)
                         , v.fraogmed
                         , v.tilogmed
                         , v.vedtaktype
                         , v.opprettet
                    from behandling_vedtak bv
                
                    inner join vedtak v
                    on bv.vedtakid = v.id
                    
                    inner join sak s
                    on s.id = bv.sakid
                
                    where v.tilogmed >= :dato
                
                )
                
                select s.sakid
                     , s.saksnummer
                     , s.bid
                     , s.fraOgMed
                     , s.tilOgMed
                     , s.vedtaktype
                     , s.opprettet
                     , case when ( EXISTS( select 1
                                             from grunnlag_fradrag g
                                            where g.behandlingid = s.bid
                                              and g.fradragstype in ('NAVytelserTilLivsopphold', 'OffentligPensjon')
                                              and ( not ( (s.tilOgMed < g.fraogmed) or (s.fraOgMed > g.tilogmed) ) )
                         ) ) then 'MANUELL'
                         else 'AUTOMATISK'
                         end behandlingtype
                       from sakogid s
            """.trimIndent()
                .hentListe(mapOf("dato" to dato), session) {
                    it.toAutomatiskEllerManuelleSak()
                }
        }
    }

    private fun Row.toAutomatiskEllerManuelleSak(): AutomatiskEllerManuellSak {
        val sakId = uuid("sakid")
        val behandlingId = uuid("bid")
        val saksnummer = Saksnummer(long("saksnummer"))
        val opprettet = tidspunkt("opprettet")
        val fraOgMed = localDate("fraOgMed")
        val tilOgMed = localDate("tilOgMed")
        val vedtakType = when (VedtakType.valueOf(string("vedtaktype"))) {
            VedtakType.SØKNAD -> no.nav.su.se.bakover.domain.regulering.VedtakType.SØKNAD
            VedtakType.AVSLAG -> no.nav.su.se.bakover.domain.regulering.VedtakType.AVSLAG
            VedtakType.ENDRING -> no.nav.su.se.bakover.domain.regulering.VedtakType.ENDRING
            VedtakType.INGEN_ENDRING -> no.nav.su.se.bakover.domain.regulering.VedtakType.INGEN_ENDRING
            VedtakType.OPPHØR -> no.nav.su.se.bakover.domain.regulering.VedtakType.OPPHØR
            VedtakType.STANS_AV_YTELSE -> no.nav.su.se.bakover.domain.regulering.VedtakType.STANS_AV_YTELSE
            VedtakType.GJENOPPTAK_AV_YTELSE -> no.nav.su.se.bakover.domain.regulering.VedtakType.GJENOPPTAK_AV_YTELSE
            VedtakType.AVVIST_KLAGE -> no.nav.su.se.bakover.domain.regulering.VedtakType.AVVIST_KLAGE
        }
        val behandlingType = BehandlingType.valueOf(string("behandlingtype"))

        return AutomatiskEllerManuellSak(
            sakId = sakId,
            saksnummer = saksnummer,
            opprettet = opprettet,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            vedtakType = vedtakType,
            behandlingType = behandlingType,
        )
    }
}
