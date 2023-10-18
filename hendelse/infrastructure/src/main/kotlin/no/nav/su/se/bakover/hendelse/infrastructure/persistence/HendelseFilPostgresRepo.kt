package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

class HendelseFilPostgresRepo(
    private val sessionFactory: SessionFactory,
) {
    fun lagre(
        sakId: UUID?,
        hendelseFil: HendelseFil,
        sessionContext: SessionContext? = null,
    ) {
        sessionContext.withOptionalSession(sessionFactory) {
            "INSERT INTO hendelse_fil (id, hendelseId, sakId, data) VALUES (:id, :hendelseId, :sakId, :data)"
                .insert(
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "hendelseId" to hendelseFil.hendelseId.value,
                        "sakId" to sakId,
                        "data" to hendelseFil.fil.getContent(),
                    ),
                    it,
                )
        }
    }

    fun hentFor(hendelseId: HendelseId, sessionContext: SessionContext): ByteArray? {
        return sessionContext.withSession {
            "SELECT * FROM hendelse_fil WHERE hendelseId=:hendelseId"
                .hent(mapOf(":hendelseId" to hendelseId), it) { it.bytes("data") }
        }
    }
}
