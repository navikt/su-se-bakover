package no.nav.su.se.bakover.database.stønadsperiode

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.jobcontext.JobContextDb
import no.nav.su.se.bakover.domain.jobcontext.NameAndYearMonthId
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.YearMonth

data class SendPåminnelseNyStønadsperiodeContextDb(
    val id: String,
    val jobName: String,
    val yearMonth: YearMonth,
    val opprettet: Tidspunkt,
    val endret: Tidspunkt,
    val prosessert: List<Long>,
    val sendt: List<Long>,
) : JobContextDb {

    override fun id(): String {
        return id
    }

    override fun toJson(): String {
        return serialize(this)
    }

    override fun toDomain(): SendPåminnelseNyStønadsperiodeContext {
        return SendPåminnelseNyStønadsperiodeContext(
            id = NameAndYearMonthId(
                name = jobName,
                yearMonth = yearMonth,
            ),
            opprettet = opprettet,
            endret = endret,
            prosessert = prosessert.map { Saksnummer(it) }.toSet(),
            sendt = sendt.map { Saksnummer(it) }.toSet(),
        )
    }

    companion object {
        fun SendPåminnelseNyStønadsperiodeContext.toDb(): SendPåminnelseNyStønadsperiodeContextDb {
            return SendPåminnelseNyStønadsperiodeContextDb(
                id = id().value(),
                jobName = id().name,
                yearMonth = id().yearMonth,
                opprettet = opprettet(),
                endret = endret(),
                prosessert = prosessert().map { it.nummer },
                sendt = sendt().map { it.nummer },
            )
        }

        fun fromDbJson(json: String): SendPåminnelseNyStønadsperiodeContext {
            return deserialize<SendPåminnelseNyStønadsperiodeContextDb>(json).toDomain()
        }
    }
}
