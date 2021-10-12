package no.nav.su.se.bakover.database.nøkkeltall

import kotliquery.Row
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import javax.sql.DataSource

internal class NøkkeltallPostgresRepo(
    private val dataSource: DataSource,
) : NøkkeltallRepo {
    override fun hentNøkkeltall(): Nøkkeltall? {
        return dataSource.withSession {
            session ->
            """
                with søknadsinfo as (
                    select *
                    from søknad s
                             left join behandling b on s.id = b.søknadid),

                behandlingsstatus as (select status, count(*) antal from søknadsinfo group by status)

                select count(*) as totalt,
                       (select antal iverksattAvslag from behandlingsstatus where status = 'IVERKSATT_AVSLAG'),
                    (select antal iverksattInnvilget from behandlingsstatus where status = 'IVERKSATT_INNVILGET'),
                    (select antal ikkePåbegynt from behandlingsstatus where status is null),
                       (select sum(antal) påbegynt from behandlingsstatus where status is not null and status not like '%IVERKSATT%'),
                       (select count(*) digitalsøknader from søknadsinfo where søknadsinfo.søknadinnhold -> 'forNav' ->> 'type' = 'DigitalSøknad' ),
                       (select count(*) papirsøknader from søknadsinfo where søknadsinfo.søknadinnhold -> 'forNav' ->> 'type' = 'Papirsøknad' ),
                       (select count(*) personer from sak)
                from søknadsinfo;
            """.trimIndent().hent(mapOf(), session) { row ->
                row.toNøkkeltall()
            }
        }
    }

    private fun Row.toNøkkeltall(): Nøkkeltall = Nøkkeltall(
        totalt = int("totalt"),
        iverksattAvslag = int("iverksattAvslag"),
        iverksattInnvilget = int("iverksattInnvilget"),
        ikkePåbegynt = int("ikkePåbegynt"),
        påbegynt = int("påbegynt"),
        digitalsøknader = int("digitalsøknader"),
        papirsøknader = int("papirsøknader"),
        personer = int("personer")
    )
}
