package no.nav.su.se.bakover.database.statistikk

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.statistikk.StønadMånedStatistikkRepo
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import statistikk.domain.StønadstatistikkMåned
import java.time.YearMonth

class StønadMånedStatistikkRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : StønadMånedStatistikkRepo {
    override fun hentMånedStatistikk(måned: YearMonth): List<StønadstatistikkMåned> {
        return dbMetrics.timeQuery("hentStønadstatistikkMåned") {
            sessionFactory.withSession { session ->
                """
                SELECT id, maaned, vedtaksdato, personnummer FROM stoenad_maaned_statistikk
                WHERE maaned = :maaned
                """.trimIndent()
                    .hentListe(
                        params = mapOf("maaned" to måned.atDay(1)),
                        session = session,
                    ) { row ->
                        with(row) {
                            StønadstatistikkMåned(
                                id = uuid("id"),
                                måned = måned,
                                vedtaksdato = localDate("vedtaksdato"),
                                personnummer = Fnr(string("personnummer")),
                                månedsbeløp = StønadstatistikkDto.Månedsbeløp(
                                    måned = måned.toString(),
                                    stonadsklassifisering = StønadsklassifiseringDto.BOR_ALENE,
                                    bruttosats = 0,
                                    nettosats = 0,
                                    inntekter = emptyList(),
                                    fradragSum = 0,
                                ),
                            )
                        }
                    }
            }
        }
    }

    override fun lagreMånedStatistikk(månedStatistikk: StønadstatistikkMåned) {
        return dbMetrics.timeQuery("lagreHendelseStønadstatistikkDto") {
            sessionFactory.withSession { session ->
                """
                    INSERT INTO stoenad_maaned_statistikk (
                        id, maaned, vedtaksdato, personnummer 
                    ) VALUES (
                        :id, :maaned, :vedtaksdato, :personnummer 
                    )
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to månedStatistikk.id,
                            "maaned" to månedStatistikk.måned.atDay(1),
                            "vedtaksdato" to månedStatistikk.vedtaksdato,
                            "personnummer" to månedStatistikk.personnummer.toString(),
                        ),
                        session = session,
                    )
            }
        }
    }
}
