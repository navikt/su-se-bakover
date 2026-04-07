package no.nav.su.se.bakover.database.regulering

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import java.util.UUID
import kotlin.to

class ReguleringKjøringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : ReguleringKjøringRepo {

    override fun lagre(oppsummering: ReguleringKjøring) {
        dbMetrics.timeQuery("lagreReguleringKjøring") {
            sessionFactory.withSession { session ->
                """
                    insert into reguleringskjøring (
                        id, 
                        aar, 
                        type,
                        dryrun,
                        start_tid, 
                        saker_antall,
                        saker_ikke_loepende,
                        saker_ikke_loepende_antall,
                        saker_allerede_reguelert,
                        saker_allerede_reguelert_antall,
                        saker_maa_revurderes,
                        saker_maa_revurderes_antall,
                        reguleringer_som_feilet,       
                        reguleringer_som_feilet_antall,  
                        reguleringer_allerede_aapen,          
                        reguleringer_allerede_aapen_antall,
                        reguleringer_manuell,          
                        reguleringer_manuell_antall,   
                        reguleringer_automatisk,       
                        reguleringer_automatisk_antall                       
                    ) values (
                        :id, 
                        :aar, 
                        :type, 
                        :dryrun, 
                        :start_tid, 
                        :saker_antall,
                        :saker_ikke_loepende,
                        :saker_ikke_loepende_antall,
                        :saker_allerede_reguelert,
                        :saker_allerede_reguelert_antall,
                        :saker_maa_revurderes,
                        :saker_maa_revurderes_antall,
                        :reguleringer_som_feilet,       
                        :reguleringer_som_feilet_antall,  
                        :reguleringer_allerede_aapen,          
                        :reguleringer_allerede_aapen_antall,
                        :reguleringer_manuell,          
                        :reguleringer_manuell_antall,   
                        :reguleringer_automatisk,       
                        :reguleringer_automatisk_antall                       
                    )
                """.trimIndent().insert(
                    mapOf(
                        "id" to oppsummering.id,
                        "aar" to oppsummering.aar,
                        "type" to oppsummering.type,
                        "dryrun" to oppsummering.dryrun,
                        "start_tid" to oppsummering.startTid,
                        "saker_antall" to oppsummering.sakerAntall,
                        "saker_ikke_loepende" to oppsummering.sakerIkkeLøpende.toString(),
                        "saker_ikke_loepende_antall" to oppsummering.sakerIkkeLøpende.size,
                        "saker_allerede_reguelert" to oppsummering.sakerAlleredeRegulert.toString(),
                        "saker_allerede_reguelert_antall" to oppsummering.sakerAlleredeRegulert.size,
                        "saker_maa_revurderes" to oppsummering.sakerMåRevurderes.toString(),
                        "saker_maa_revurderes_antall" to oppsummering.sakerMåRevurderes.size,
                        "reguleringer_som_feilet" to oppsummering.reguleringerSomFeilet.toString(),
                        "reguleringer_som_feilet_antall" to oppsummering.reguleringerSomFeilet.size,
                        "reguleringer_allerede_aapen" to oppsummering.reguleringerAlleredeÅpen.toString(),
                        "reguleringer_allerede_aapen_antall" to oppsummering.reguleringerAlleredeÅpen.size,
                        "reguleringer_manuell" to oppsummering.reguleringerManuell.toString(),
                        "reguleringer_manuell_antall" to oppsummering.reguleringerManuell.size,
                        "reguleringer_automatisk" to oppsummering.reguleringerAutomatisk.toString(),
                        "reguleringer_automatisk_antall" to oppsummering.reguleringerAutomatisk.size,
                    ),
                    session,
                )
            }
        }
    }

    override fun hent(id: UUID): ReguleringKjøring? {
        return sessionFactory.withSession { session ->
            """
                select * from reguleringskjøring where id = :id
            """.trimIndent().hent(
                params = mapOf("id" to id),
                session = session,
            ) { row ->
                ReguleringKjøring(
                    id = row.uuid("id"),
                    aar = row.int("aar"),
                    type = row.string("type"),
                    dryrun = row.boolean("dryrun"),
                    startTid = row.localDateTime("start_tid"),
                    sakerAntall = row.int("saker_antall"),
                    sakerIkkeLøpende = Json.parseToJsonElement(row.string("saker_ikke_loepende")).jsonArray,
                    sakerAlleredeRegulert = Json.parseToJsonElement(row.string("saker_allerede_reguelert")).jsonArray,
                    sakerMåRevurderes = Json.parseToJsonElement(row.string("saker_maa_revurderes")).jsonArray,
                    reguleringerSomFeilet = Json.parseToJsonElement(row.string("reguleringer_som_feilet")).jsonArray,
                    reguleringerAlleredeÅpen = Json.parseToJsonElement(row.string("reguleringer_allerede_aapen")).jsonArray,
                    reguleringerManuell = Json.parseToJsonElement(row.string("reguleringer_manuell")).jsonArray,
                    reguleringerAutomatisk = Json.parseToJsonElement(row.string("reguleringer_automatisk")).jsonArray,

                )
            }
        }
    }
}
