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

    override fun lagre(kjøring: ReguleringKjøring) {
        dbMetrics.timeQuery("lagreReguleringKjøring") {
            sessionFactory.withSession { session ->

                val sakerAlleredeRegulert = if (kjøring is ReguleringKjøring.Grunnbeløp) kjøring.sakerAlleredeRegulert else emptyList()
                val sakerMåRevurderes = if (kjøring is ReguleringKjøring.Grunnbeløp) kjøring.sakerMåRevurderes else emptyList()
                val reguleringerAutomatisk = if (kjøring is ReguleringKjøring.Grunnbeløp) kjøring.reguleringerAutomatisk else emptyList()

                val skalIkkeOmregnes = if (kjøring is ReguleringKjøring.Aldersfradrag) kjøring.skalIkkeOmregnes else emptyList()

                val params = mapOf(
                    "id" to kjøring.id,
                    "aar" to kjøring.aar,
                    "type" to kjøring.type.name,
                    "dryrun" to kjøring.dryrun,
                    "start_tid" to kjøring.startTid,
                    "saker_antall" to kjøring.sakerAntall,
                    "saker_ikke_loepende" to serialize(kjøring.sakerIkkeLøpende),
                    "saker_ikke_loepende_antall" to kjøring.sakerIkkeLøpende.size,
                    "saker_allerede_reguelert" to serialize(sakerAlleredeRegulert),
                    "saker_allerede_reguelert_antall" to sakerAlleredeRegulert.size,
                    "saker_maa_revurderes" to serialize(sakerMåRevurderes),
                    "saker_maa_revurderes_antall" to sakerMåRevurderes.size,
                    "reguleringer_som_feilet" to serialize(kjøring.reguleringerSomFeilet),
                    "reguleringer_som_feilet_antall" to kjøring.reguleringerSomFeilet.size,
                    "reguleringer_allerede_aapen" to serialize(kjøring.reguleringerAlleredeÅpen),
                    "reguleringer_allerede_aapen_antall" to kjøring.reguleringerAlleredeÅpen.size,
                    "reguleringer_manuell" to serialize(kjøring.reguleringerManuell),
                    "reguleringer_manuell_antall" to kjøring.reguleringerManuell.size,
                    "reguleringer_automatisk" to serialize(reguleringerAutomatisk),
                    "reguleringer_automatisk_antall" to reguleringerAutomatisk.size,
                    "reguleringer_skal_ikke_omregnes" to serialize(skalIkkeOmregnes),
                    "reguleringer_skal_ikke_omregnes_antall" to skalIkkeOmregnes.size,
                )

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
                        reguleringer_automatisk_antall,
                        reguleringer_skal_ikke_omregnes,
                        reguleringer_skal_ikke_omregnes_antall
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
                        :reguleringer_automatisk_antall,
                        :reguleringer_skal_ikke_omregnes,
                        :reguleringer_skal_ikke_omregnes_antall
                    )
                """.trimIndent().insert(params, session)
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
    val type = ReguleringKjøring.Type.valueOf(string("type"))
    return when (type) {
        ReguleringKjøring.Type.GRUNNBELØP -> {
            ReguleringKjøring.Grunnbeløp(
                id = uuid("id"),
                aar = int("aar"),
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
        ReguleringKjøring.Type.ALDERSFRADRAG -> {
            ReguleringKjøring.Aldersfradrag(
                id = uuid("id"),
                aar = int("aar"),
                dryrun = boolean("dryrun"),
                startTid = localDateTime("start_tid"),
                sakerAntall = int("saker_antall"),
                sakerIkkeLøpende = string("saker_ikke_loepende").deserializeList(),
                reguleringerSomFeilet = string("reguleringer_som_feilet").deserializeList(),
                reguleringerAlleredeÅpen = string("reguleringer_allerede_aapen").deserializeList(),
                reguleringerManuell = string("reguleringer_manuell").deserializeList(),
                skalIkkeOmregnes = string("reguleringer_skal_ikke_omregnes").deserializeList(),
            )
        }
    }
}
