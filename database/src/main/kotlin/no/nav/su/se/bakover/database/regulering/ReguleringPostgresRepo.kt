package no.nav.su.se.bakover.database.regulering

import kotliquery.Row
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.vedtak.VedtakType
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.VedtakSomKanReguleres
import java.time.LocalDate

internal class ReguleringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : ReguleringRepo {
    override fun hentVedtakSomKanReguleres(fraOgMed: LocalDate): List<VedtakSomKanReguleres> {
        return dbMetrics.timeQuery("hentVedtakSomKanReguleres") {
            sessionFactory.withSession { session ->
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
                                              and ( (s.tilOgMed >= g.fraogmed) and (:dato <= g.tilogmed) )
                         ) ) then 'MANUELL'
                         else 'AUTOMATISK'
                         end behandlingtype
                       from sakogid s
                """.trimIndent()
                    .hentListe(mapOf("dato" to fraOgMed), session) {
                        it.toVedtakSomKanReguleres()
                    }
            }
        }
    }

    private fun Row.toVedtakSomKanReguleres(): VedtakSomKanReguleres {
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
        val reguleringType = ReguleringType.valueOf(string("behandlingtype"))

        return VedtakSomKanReguleres(
            sakId = sakId,
            saksnummer = saksnummer,
            opprettet = opprettet,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            vedtakType = vedtakType,
            reguleringType = reguleringType,
        )
    }
}
