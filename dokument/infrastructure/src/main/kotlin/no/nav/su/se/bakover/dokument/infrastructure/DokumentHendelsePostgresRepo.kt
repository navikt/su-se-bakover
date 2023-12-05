package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.hendelser.DistribuertDokument
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokument
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokument
import dokument.domain.hendelser.JournalførtDokumentHendelse
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.dokument.infrastructure.DistribuertDokumentHendelseDbJson.Companion.dataDbJson
import no.nav.su.se.bakover.dokument.infrastructure.GenerertDokumentHendelseDbJson.Companion.toDbJson
import no.nav.su.se.bakover.dokument.infrastructure.JournalførtDokumentHendelseDbJson.Companion.dataDbJson
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseFilPostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.toDbJson
import java.util.UUID

class DokumentHendelsePostgresRepo(
    private val hendelseRepo: HendelseRepo,
    private val hendelseFilRepo: HendelseFilPostgresRepo,
    private val sessionFactory: SessionFactory,
) : DokumentHendelseRepo {

    override fun lagre(
        hendelse: DokumentHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext?,
    ) {
        lagreHendelse(
            hendelse = hendelse,
            meta = meta,
            null,
            sessionContext = sessionContext,
        )
    }

    override fun lagre(
        hendelse: DokumentHendelse,
        meta: DefaultHendelseMetadata,
        hendelseFil: HendelseFil,
        sessionContext: SessionContext?,
    ) {
        lagreHendelse(
            hendelse = hendelse,
            meta = meta,
            hendelseFil = hendelseFil,
            sessionContext = sessionContext,
        )
    }

    private fun lagreHendelse(
        hendelse: DokumentHendelse,
        meta: DefaultHendelseMetadata,
        hendelseFil: HendelseFil?,
        sessionContext: SessionContext?,
    ) {
        (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
            hendelse = hendelse,
            type = when (hendelse) {
                is GenerertDokumentHendelse -> GenerertDokument
                is JournalførtDokumentHendelse -> JournalførtDokument
                is DistribuertDokumentHendelse -> DistribuertDokument
            },
            data = when (hendelse) {
                is JournalførtDokumentHendelse -> hendelse.dataDbJson(hendelse.relatertHendelse)
                is GenerertDokumentHendelse -> hendelse.dokumentUtenFil.toDbJson(
                    hendelse.relatertHendelse,
                    hendelse.skalSendeBrev,
                )

                is DistribuertDokumentHendelse -> hendelse.dataDbJson(hendelse.relatertHendelse)
            },
            sessionContext = sessionContext,
            meta = meta.toDbJson(),
        )
        hendelseFil?.let {
            hendelseFilRepo.lagre(
                sakId = hendelse.sakId,
                hendelseFil = it,
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext?): List<DokumentHendelse> {
        return (hendelseRepo as HendelsePostgresRepo).let { repo ->
            listOf(
                GenerertDokument,
                JournalførtDokument,
                DistribuertDokument,
            ).flatMap {
                repo.hentHendelserForSakIdOgType(
                    sakId = sakId,
                    type = it,
                    sessionContext = sessionContext,
                ).map { it.toDokumentHendelse() }
            }
        }
    }

    override fun hentHendelse(hendelseId: HendelseId, sessionContext: SessionContext?): DokumentHendelse? {
        return (hendelseRepo as HendelsePostgresRepo)
            .hentHendelseForHendelseId(hendelseId, sessionContext)?.toDokumentHendelse()
    }

    override fun hentFilFor(hendelseId: HendelseId, sessionContext: SessionContext?): HendelseFil? {
        return hendelseFilRepo.hentFor(hendelseId, sessionContext)
    }

    override fun hentHendelseOgFilFor(
        hendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): Pair<DokumentHendelse?, HendelseFil?> {
        return Pair(hentHendelse(hendelseId, sessionContext), hentFilFor(hendelseId, sessionContext))
    }

    override fun hentHendelseOgFilForDokument(
        dokumentId: UUID,
        sessionContext: SessionContext?,
    ): Pair<DokumentHendelse?, HendelseFil?> {
        return sessionFactory.withSessionContext(sessionContext) { tx ->
            val hendelseOgFil = tx.withOptionalSession(sessionFactory) {
                """
                select hendelseid from hendelse where data ->> 'id' = :dokumentId
                """.trimIndent().hent(
                    mapOf("dokumentId" to dokumentId.toString()),
                    it,
                ) {
                    hentHendelseOgFilFor(HendelseId.fromString(it.string("hendelseid")), tx)
                }
            }
            Pair(hendelseOgFil?.first, hendelseOgFil?.second)
        }
    }

    override fun hentDokumentHendelseForRelatert(
        relatertHendelseId: HendelseId,
        sessionContext: SessionContext?,
    ): DokumentHendelse? {
        return sessionContext?.withOptionalSession(sessionFactory) {
            """
            select * from hendelse where data ->> 'relatertHendelse' = :relatertHendelseId
            """.trimIndent().hent(
                mapOf("relatertHendelseId" to relatertHendelseId.toString()),
                it,
            ) {
                HendelsePostgresRepo.toPersistertHendelse(it)
            }?.toDokumentHendelse()
        }
    }
}

private fun PersistertHendelse.toDokumentHendelse(): DokumentHendelse {
    return when (this.type) {
        GenerertDokument -> GenerertDokumentHendelseDbJson.toDomain(
            type = this.type,
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
        )

        JournalførtDokument -> JournalførtDokumentHendelseDbJson.toDomain(
            type = type,
            data = data,
            hendelseId = hendelseId,
            sakId = sakId!!,
            hendelsestidspunkt = hendelsestidspunkt,
            versjon = versjon,
        )

        DistribuertDokument -> DistribuertDokumentHendelseDbJson.toDomain(
            type = type,
            data = data,
            hendelseId = hendelseId,
            sakId = sakId!!,
            hendelsestidspunkt = hendelsestidspunkt,
            versjon = versjon,
        )

        else -> throw IllegalStateException("Ugyldig type for dokument hendelse. type var $type")
    }
}
