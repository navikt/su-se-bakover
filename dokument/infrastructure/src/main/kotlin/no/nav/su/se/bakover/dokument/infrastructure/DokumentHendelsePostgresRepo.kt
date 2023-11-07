package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.DokumentHendelseRepo
import dokument.domain.hendelser.GenerertDokument
import dokument.domain.hendelser.GenerertDokumentForArkiveringHendelse
import dokument.domain.hendelser.GenerertDokumentForJournalføring
import dokument.domain.hendelser.GenerertDokumentForJournalføringHendelse
import dokument.domain.hendelser.GenerertDokumentForUtsendelse
import dokument.domain.hendelser.GenerertDokumentForUtsendelseHendelse
import dokument.domain.hendelser.JournalførtDokumentForArkivering
import dokument.domain.hendelser.JournalførtDokumentForArkiveringHendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelseHendelse
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.dokument.infrastructure.GenerertDokumentHendelseDbJson.Companion.toDbJson
import no.nav.su.se.bakover.dokument.infrastructure.JournalførtDokumentHendelseDbJson.Companion.dataDbJson
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseFilPostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import java.util.UUID

class DokumentHendelsePostgresRepo(
    private val hendelseRepo: HendelseRepo,
    private val hendelseFilRepo: HendelseFilPostgresRepo,
    private val sessionFactory: SessionFactory,
) : DokumentHendelseRepo {
    override fun lagre(hendelse: DokumentHendelse, sessionContext: SessionContext?) {
        lagreHendelse(hendelse, null, sessionContext)
    }

    override fun lagre(hendelse: DokumentHendelse, hendelseFil: HendelseFil, sessionContext: SessionContext?) {
        lagreHendelse(hendelse, hendelseFil, sessionContext)
    }

    private fun lagreHendelse(hendelse: DokumentHendelse, hendelseFil: HendelseFil?, sessionContext: SessionContext?) {
        (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
            hendelse = hendelse,
            type = when (hendelse) {
                is GenerertDokumentForJournalføringHendelse -> GenerertDokumentForJournalføring
                is GenerertDokumentForUtsendelseHendelse -> GenerertDokumentForUtsendelse
                is GenerertDokumentForArkiveringHendelse -> GenerertDokument
                is JournalførtDokumentForArkiveringHendelse -> JournalførtDokumentForArkivering
                is JournalførtDokumentForUtsendelseHendelse -> JournalførtDokumentForUtsendelse
            },
            data = when (hendelse) {
                is JournalførtDokumentForArkiveringHendelse -> hendelse.dataDbJson(hendelse.relaterteHendelser)
                is JournalførtDokumentForUtsendelseHendelse -> hendelse.dataDbJson(hendelse.relaterteHendelser)
                is GenerertDokumentForArkiveringHendelse -> hendelse.dokumentUtenFil.toDbJson(hendelse.relaterteHendelser)
                is GenerertDokumentForJournalføringHendelse -> hendelse.dokumentUtenFil.toDbJson(hendelse.relaterteHendelser)
                is GenerertDokumentForUtsendelseHendelse -> hendelse.dokumentUtenFil.toDbJson(hendelse.relaterteHendelser)
            },
            sessionContext = sessionContext,
        )
        hendelseFil?.let {
            hendelseFilRepo.lagre(
                sakId = hendelse.sakId,
                hendelseFil = it,
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext): List<DokumentHendelse> {
        return (hendelseRepo as HendelsePostgresRepo).let { repo ->
            listOf(GenerertDokument, GenerertDokumentForJournalføring, GenerertDokumentForUtsendelse).flatMap {
                repo.hentHendelserForSakIdOgType(
                    sakId = sakId,
                    type = GenerertDokument,
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
        val hendelseOgFil = sessionContext.withOptionalSession(sessionFactory) {
            """
                select hendelseid from hendelse where data ->> 'id' = :dokumentId
            """.trimIndent().hent(
                mapOf("dokumentId" to dokumentId.toString()),
                it,
            ) {
                return@hent hentHendelseOgFilFor(HendelseId.fromString(it.string("hendelseid")), sessionContext)
            }
        }
        return Pair(hendelseOgFil?.first, hendelseOgFil?.second)
    }
}

private fun PersistertHendelse.toDokumentHendelse(): DokumentHendelse {
    return when (this.type) {
        GenerertDokument, GenerertDokumentForJournalføring, GenerertDokumentForUtsendelse -> GenerertDokumentHendelseDbJson.toDomain(
            type = this.type,
            data = this.data,
            hendelseId = this.hendelseId,
            sakId = this.sakId!!,
            hendelsestidspunkt = this.hendelsestidspunkt,
            versjon = this.versjon,
            meta = this.defaultHendelseMetadata(),
        )

        JournalførtDokumentForArkivering, JournalførtDokumentForUtsendelse -> JournalførtDokumentHendelseDbJson.toDomain(
            type = type,
            data = data,
            hendelseId = hendelseId,
            sakId = sakId!!,
            hendelsestidspunkt = hendelsestidspunkt,
            versjon = versjon,
            meta = this.defaultHendelseMetadata(),
        )

        else -> throw IllegalStateException("Ugyldig type for dokument hendelse. type var $type")
    }
}
