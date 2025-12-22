package no.nav.su.se.bakover.database.statistikk

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.domain.statistikk.AvslagsvedtakUtenFritekst
import no.nav.su.se.bakover.domain.statistikk.FritekstAvslagRepo
import java.time.YearMonth

class FritekstAvslagRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
) : FritekstAvslagRepo {
    override fun hentAntallAvslagsvedtakUtenFritekst(): List<AvslagsvedtakUtenFritekst> {
        return sessionFactory.withTransaction { tx ->
            //language=SQL
            """
        SELECT count(d.generertdokumentjson) AS count,
               to_char(date_trunc('month', v.opprettet), 'YYYY-MM') AS grupperingsdato
        FROM vedtak v
        JOIN dokument d ON v.id = d.vedtakid
        WHERE length(trim(d.generertdokumentjson ->> 'fritekst')) < 1
          AND v.vedtaktype = 'AVSLAG'
        GROUP BY grupperingsdato
        """.hentListe(session = tx) { row ->
                AvslagsvedtakUtenFritekst(
                    antall = row.long("count").toInt(),
                    yearMonth = YearMonth.parse(row.string("grupperingsdato")),
                )
            }
        }
    }
}
