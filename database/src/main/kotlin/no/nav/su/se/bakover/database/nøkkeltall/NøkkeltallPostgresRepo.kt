package no.nav.su.se.bakover.database.nøkkeltall

import kotliquery.Row
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import nøkkeltall.domain.Nøkkeltall
import nøkkeltall.domain.NøkkeltallRepo
import java.time.Clock
import java.time.LocalDate

internal class NøkkeltallPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
) : NøkkeltallRepo {
    override fun hentNøkkeltall(sakstype: Sakstype): Nøkkeltall {
        return dbMetrics.timeQuery("hentNøkkeltall") {
            sessionFactory.withSession { session ->
                """
                with søknadsinfo as (select s.lukket, s.søknadinnhold, b.status from søknad s left join behandling b on s.id = b.søknadid left join sak ss on ss.id = s.sakid WHERE ss.type = :sakstype),
                     behandlingsstatus as (select status, count(*) antall from søknadsinfo group by status),
                     gjeldende_vedtak as (select * from vedtak left join sak ss on ss.id = vedtak.sakid where :dato >= vedtak.fraogmed and :dato <= vedtak.tilogmed AND ss.type = :sakstype),
                     innvilgelser as (select count(*) from gjeldende_vedtak where vedtaktype = 'SØKNAD'),
                     opphør as (select count(*) from gjeldende_vedtak where vedtaktype = 'OPPHØR')

                select
                (select count(*) from søknadsinfo) as totalt,
                (select count(*) from søknadsinfo where søknadsinfo.lukket is null and status is not null and status not like '%IVERKSATT%') as påbegynt,
                (select count(*) from søknadsinfo where søknadsinfo.lukket is null and status is null) as ikkePåbegynt,
                (select count(*) from søknadsinfo where søknadsinfo.lukket is not null) as lukket,
                (select count(*) from søknadsinfo where søknadsinfo.søknadinnhold -> 'forNav' ->> 'type' = 'DigitalSøknad') as digitalsøknader,
                (select count(*) from søknadsinfo where søknadsinfo.søknadinnhold -> 'forNav' ->> 'type' = 'Papirsøknad') as papirsøknader,
                coalesce((select antall from behandlingsstatus where status = 'IVERKSATT_AVSLAG'), 0) as iverksattAvslag,
                coalesce((select antall from behandlingsstatus where status = 'IVERKSATT_INNVILGET' ), 0) as iverksattInnvilget,
                (select count(*) from sak) as personer,
                (select (select * from innvilgelser) - (select * from opphør)) as løpendeSaker;
                """.trimIndent().hent(mapOf("dato" to LocalDate.now(clock), "sakstype" to sakstype.value), session) { row ->
                    row.toNøkkeltall()
                }
            }!!
        }
    }

    private fun Row.toNøkkeltall(): Nøkkeltall = Nøkkeltall(
        søknader = Nøkkeltall.Søknader(
            totaltAntall = int("totalt"),
            iverksatteAvslag = int("iverksattAvslag"),
            iverksatteInnvilget = int("iverksattInnvilget"),
            ikkePåbegynt = int("ikkePåbegynt"),
            påbegynt = int("påbegynt"),
            lukket = int("lukket"),
            digitalsøknader = int("digitalsøknader"),
            papirsøknader = int("papirsøknader"),
        ),
        antallUnikePersoner = int("personer"),
        løpendeSaker = int("løpendeSaker"),
    )
}
