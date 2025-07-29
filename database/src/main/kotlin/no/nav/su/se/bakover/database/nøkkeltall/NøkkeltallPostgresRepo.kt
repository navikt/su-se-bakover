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
                    WITH søknadsinfo AS (
                        SELECT 
                            s.lukket, 
                            s.søknadinnhold, 
                            b.status 
                        FROM søknad s 
                        LEFT JOIN behandling b ON s.id = b.søknadid 
                        LEFT JOIN sak ss ON ss.id = s.sakid 
                        WHERE ss.type = :sakstype
                    ),
                    søknad_aggregates AS (
                        SELECT 
                            COUNT(*) AS totalt,
                            COUNT(*) FILTER (
                                WHERE lukket IS NULL AND status IS NOT NULL AND status NOT LIKE '%IVERKSATT%'
                            ) AS påbegynt,
                            COUNT(*) FILTER (
                                WHERE lukket IS NULL AND status IS NULL
                            ) AS ikkePåbegynt,
                            COUNT(*) FILTER (
                                WHERE lukket IS NOT NULL
                            ) AS lukket,
                            COUNT(*) FILTER (
                                WHERE søknadinnhold -> 'forNav' ->> 'type' = 'DigitalSøknad'
                            ) AS digitalsøknader,
                            COUNT(*) FILTER (
                                WHERE søknadinnhold -> 'forNav' ->> 'type' = 'Papirsøknad'
                            ) AS papirsøknader,
                            COUNT(*) FILTER (
                                WHERE status = 'IVERKSATT_AVSLAG'
                            ) AS iverksattAvslag,
                            COUNT(*) FILTER (
                                WHERE status = 'IVERKSATT_INNVILGET'
                            ) AS iverksattInnvilget
                        FROM søknadsinfo
                    ),
                    vedtak_aggregates AS (
                        SELECT 
                            COUNT(*) FILTER (
                                WHERE vedtaktype = 'SØKNAD'
                            ) AS innvilgelser,
                            COUNT(*) FILTER (
                                WHERE vedtaktype = 'OPPHØR'
                            ) AS opphør
                        FROM vedtak 
                        LEFT JOIN sak ss ON ss.id = vedtak.sakid 
                        WHERE :dato BETWEEN vedtak.fraogmed AND vedtak.tilogmed 
                          AND ss.type = :sakstype
                    ),
                    personer AS (
                        SELECT COUNT(*) AS personer FROM sak
                    )

                    SELECT 
                        sa.totalt,
                        sa.påbegynt,
                        sa.ikkePåbegynt,
                        sa.lukket,
                        sa.digitalsøknader,
                        sa.papirsøknader,
                        sa.iverksattAvslag,
                        sa.iverksattInnvilget,
                        p.personer,
                        (va.innvilgelser - va.opphør) AS løpendeSaker
                    FROM søknad_aggregates sa, vedtak_aggregates va, personer p;
                    
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
