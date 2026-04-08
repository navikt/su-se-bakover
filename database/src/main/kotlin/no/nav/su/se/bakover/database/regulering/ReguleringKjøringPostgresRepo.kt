package no.nav.su.se.bakover.database.regulering

import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
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
                        "saker_ikke_loepende" to serialize(oppsummering.sakerIkkeLøpende),
                        "saker_ikke_loepende_antall" to oppsummering.sakerIkkeLøpende.size,
                        "saker_allerede_reguelert" to serialize(oppsummering.sakerAlleredeRegulert),
                        "saker_allerede_reguelert_antall" to oppsummering.sakerAlleredeRegulert.size,
                        "saker_maa_revurderes" to serialize(oppsummering.sakerMåRevurderes),
                        "saker_maa_revurderes_antall" to oppsummering.sakerMåRevurderes.size,
                        "reguleringer_som_feilet" to serialize(oppsummering.reguleringerSomFeilet),
                        "reguleringer_som_feilet_antall" to oppsummering.reguleringerSomFeilet.size,
                        "reguleringer_allerede_aapen" to serialize(oppsummering.reguleringerAlleredeÅpen),
                        "reguleringer_allerede_aapen_antall" to oppsummering.reguleringerAlleredeÅpen.size,
                        "reguleringer_manuell" to serialize(oppsummering.reguleringerManuell),
                        "reguleringer_manuell_antall" to oppsummering.reguleringerManuell.size,
                        "reguleringer_automatisk" to serialize(oppsummering.reguleringerAutomatisk),
                        "reguleringer_automatisk_antall" to oppsummering.reguleringerAutomatisk.size,
                    ),
                    session,
                )
            }
        }
    }

    override fun hent(): List<ReguleringKjøring> {
        return sessionFactory.withSession { session ->
            """
                select * from reguleringskjøring
            """.trimIndent().hentListe(
                params = emptyMap(),
                session = session,
            ) { row -> row.toReguleringKjøring() }
        }
    }
}

private fun Row.toReguleringKjøring(): ReguleringKjøring {
    return ReguleringKjøring(
        id = uuid("id"),
        aar = int("aar"),
        type = string("type"),
        dryrun = boolean("dryrun"),
        startTid = localDateTime("start_tid"),
        sakerAntall = int("saker_antall"),
        sakerIkkeLøpende = string("saker_ikke_loepende").deserializeList(),
        sakerAlleredeRegulert = string("saker_allerede_reguelert").deserializeList(),
        sakerMåRevurderes = string("saker_maa_revurderes").deserializeList(),
        reguleringerSomFeilet = string("reguleringer_som_feilet").deserializeList(),
        reguleringerAlleredeÅpen = string("reguleringer_allerede_aapen").deserializeList(),
        reguleringerManuell = string("reguleringer_manuell").deserializeList(),
        reguleringerAutomatisk = string("reguleringer_automatisk").deserializeList(),
    )
}
