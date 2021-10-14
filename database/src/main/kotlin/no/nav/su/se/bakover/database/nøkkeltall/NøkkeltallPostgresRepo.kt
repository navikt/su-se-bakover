package no.nav.su.se.bakover.database.nøkkeltall

import kotliquery.Row
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import no.nav.su.se.bakover.domain.nøkkeltall.NøkkeltallRepo
import javax.sql.DataSource

internal class NøkkeltallPostgresRepo(
    private val dataSource: DataSource,
) : NøkkeltallRepo {
    override fun hentNøkkeltall(): Nøkkeltall {
        return dataSource.withSession {
            session ->
            """
                with søknadsinfo as (
                    select s.lukket, s.søknadinnhold, b.status
                    from søknad s
                             left join behandling b on s.id = b.søknadid),

                     behandlingsstatus as (select status, count(*) antal from søknadsinfo group by status)

                select count(*) as totalt,
                       coalesce((select antal from behandlingsstatus where status = 'IVERKSATT_AVSLAG'), 0) as iverksattAvslag,
                       coalesce(( select antal from behandlingsstatus where status = 'IVERKSATT_INNVILGET' ), 0) as iverksattInnvilget,
                       coalesce(( select sum(antal) from behandlingsstatus where status is not null and status not like '%IVERKSATT%'), 0) as påbegynt,
                       (select count(*) as ikkePåbegynt from søknadsinfo where søknadsinfo.lukket is null and status is null),
                       (select count(*) as digitalsøknader from søknadsinfo where søknadsinfo.søknadinnhold -> 'forNav' ->> 'type' = 'DigitalSøknad' ),
                       (select count(*) as papirsøknader from søknadsinfo where søknadsinfo.søknadinnhold -> 'forNav' ->> 'type' = 'Papirsøknad' ),
                       (select count(*) as personer from sak)
                from søknadsinfo;
            """.trimIndent().hent(mapOf(), session) { row ->
                row.toNøkkeltall()
            }
        }!!
    }

    private fun Row.toNøkkeltall(): Nøkkeltall = Nøkkeltall(
        søknader = Nøkkeltall.Søknader(
            totaltAntall = int("totalt"),
            iverksatteAvslag = int("iverksattAvslag"),
            iverksatteInnvilget = int("iverksattInnvilget"),
            ikkePåbegynt = int("ikkePåbegynt"),
            påbegynt = int("påbegynt"),
            digitalsøknader = int("digitalsøknader"),
            papirsøknader = int("papirsøknader"),
        ),
        antallUnikePersoner = int("personer")
    )
}
