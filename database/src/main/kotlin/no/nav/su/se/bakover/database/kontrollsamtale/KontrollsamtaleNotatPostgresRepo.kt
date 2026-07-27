package no.nav.su.se.bakover.database.kontrollsamtale

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.booleanOrNull
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatRepo
import java.util.UUID

internal class KontrollsamtaleNotatPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : KontrollsamtaleNotatRepo {
    override fun lagre(
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sakId: UUID,
        sessionContext: SessionContext?,
    ) {
        dbMetrics.timeQuery("lagreKontrollsamtaleNotat") {
            sessionFactory.withSession { session ->
                """
                    insert into kontrollsamtale_notat (
                    id,
                    sakid,
                    opprettet,
                    personligOppmøte,
                    fullmaktOgLegeerklæring,
                    originalPass,
                    gyldigPass,
                    harVærtUtenlands,
                    utenlandsoppholdDatoer,
                    harPlanerOmUtenlandsreise,
                    planlagteUtenlandsreiseDatoer,
                    reiseDokumentasjon,
                    økonomiskSituasjon,
                    andreForhold,
                    skatteOpplysninger,
                    fritekst
                )
                values (
                    :id,
                    :sakid,
                    :opprettet,
                    :personligOppmote,
                    :fullmaktOgLegeerklaring,
                    :originalPass,
                    :gyldigPass,
                    :harVaertUtenlands,
                    to_jsonb(:utenlandsoppholdDatoer::jsonb),
                    :harPlanerOmUtenlandsreise,
                    to_jsonb(:planlagteUtenlandsreiseDatoer::jsonb),
                    :reiseDokumentasjon,
                    :okonomiskSituasjon,
                    :andreForhold,
                    :skatteOpplysninger,
                    :fritekst
                  )
                """.trimIndent().insert(
                    mapOf(
                        "id" to kontrollsamtaleNotat.id,
                        "sakid" to sakId,
                        "opprettet" to kontrollsamtaleNotat.opprettet,
                        "personligOppmote" to kontrollsamtaleNotat.personligOppmøte,
                        "fullmaktOgLegeerklaring" to kontrollsamtaleNotat.fullmaktOgLegeerklæring,
                        "originalPass" to kontrollsamtaleNotat.originalPass,
                        "gyldigPass" to kontrollsamtaleNotat.gyldigPass,
                        "harVaertUtenlands" to kontrollsamtaleNotat.harVærtUtenlands,
                        "utenlandsoppholdDatoer" to kontrollsamtaleNotat.utenlandsoppholdDatoer.toDatabaseJson(),
                        "harPlanerOmUtenlandsreise" to kontrollsamtaleNotat.harPlanerOmUtenlandsreise,
                        "planlagteUtenlandsreiseDatoer" to kontrollsamtaleNotat.planlagteUtenlandsreiseDatoer.toDatabaseJson(),
                        "reiseDokumentasjon" to kontrollsamtaleNotat.reiseDokumentasjon,
                        "okonomiskSituasjon" to kontrollsamtaleNotat.økonomiskSituasjon,
                        "andreForhold" to kontrollsamtaleNotat.andreForhold,
                        "skatteOpplysninger" to kontrollsamtaleNotat.skatteOpplysninger,
                        "fritekst" to kontrollsamtaleNotat.fritekst,
                    ),
                    session,
                )
            }
        }
    }

    override fun hentKontrollsamtaleNotat(
        sakId: UUID,
        sessionContext: SessionContext?,
    ): KontrollsamtaleNotat? {
        return dbMetrics.timeQuery("hentKontrollsamtaleNotat") {
            sessionFactory.withSession { session ->
                """
                    select *
                    from kontrollsamtale_notat
                    where sakid = :sakId
                    order by opprettet desc
                    limit 1
                """.trimIndent().hent(
                    mapOf("sakId" to sakId),
                    session,
                ) { row ->
                    KontrollsamtaleNotat(
                        id = row.uuid("id"),
                        opprettet = row.tidspunkt("opprettet"),
                        personligOppmøte = row.boolean("personligOppmøte"),
                        fullmaktOgLegeerklæring = row.booleanOrNull("fullmaktOgLegeerklæring"),
                        originalPass = row.boolean("originalPass"),
                        gyldigPass = row.boolean("gyldigPass"),
                        harVærtUtenlands = row.boolean("harVærtUtenlands"),
                        utenlandsoppholdDatoer = row.string("utenlandsoppholdDatoer").toKontrollsamtaleReiseDatoList(),
                        harPlanerOmUtenlandsreise = row.boolean("harPlanerOmUtenlandsreise"),
                        planlagteUtenlandsreiseDatoer = row.string("planlagteUtenlandsreiseDatoer").toKontrollsamtaleReiseDatoList(),
                        reiseDokumentasjon = row.boolean("reiseDokumentasjon"),
                        økonomiskSituasjon = row.boolean("økonomiskSituasjon"),
                        andreForhold = row.boolean("andreForhold"),
                        skatteOpplysninger = row.boolean("skatteOpplysninger"),
                        fritekst = row.stringOrNull("fritekst"),
                    )
                }
            }
        }
    }
}
