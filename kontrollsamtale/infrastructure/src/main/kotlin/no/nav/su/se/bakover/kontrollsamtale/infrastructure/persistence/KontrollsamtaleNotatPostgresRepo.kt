package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotat
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleNotatRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.toDatabaseJson
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
}
