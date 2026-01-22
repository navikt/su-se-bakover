package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import arrow.core.Nel
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import org.slf4j.LoggerFactory
import java.util.UUID

class HendelsekonsumenterPostgresRepo(
    val sessionFactory: PostgresSessionFactory,
) : HendelsekonsumenterRepo {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(
        hendelser: List<HendelseId>,
        konsumentId: HendelseskonsumentId,
        context: SessionContext?,
    ) {
        hendelser.forEach {
            lagre(it, konsumentId, context)
        }
    }

    override fun lagre(
        hendelseId: HendelseId,
        konsumentId: HendelseskonsumentId,
        context: SessionContext?,
    ) {
        context.withOptionalSession(sessionFactory) {
            """
            INSERT INTO
                hendelse_konsument
                    (id, hendelseId, konsumentId)
                    values
                        (:id, :hendelseId, :konsumentId)
            """.trimIndent().insert(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "hendelseId" to hendelseId.value,
                    "konsumentId" to konsumentId.value,
                ),
                it,
            )
        }
    }

    /**
     * Kun for HendelseskonsumentId("GenererDokumentForForhåndsvarselTilbakekrevingKonsument") og ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
     * Vanligvis antok man at hvis GenererDokumentForForhåndsvarselTilbakekrevingKonsument typen var null med left join så måtte man lage et dokument
     * men dette er feil siden det ikke tok høyde for senere hendelser som er avbrutt eks:
     | hendelseid | hendelsesnummer | type | hendelsestidspunkt | tidligerehendelseid |
     | :--- | :--- | :--- | :--- | :--- |
     | 6afc6772-31bf-4715-a06c-70ff5a4d16af | 7729 | OPPRETTET\_TILBAKEKREVINGSBEHANDLING | 2025-12-02 12:06:39.846925 +00:00 | null |
     | 4319ab24-9226-4407-91f1-b26e3fbc0746 | 7730 | FORHÅNDSVARSLET\_TILBAKEKREVINGSBEHANDLING | 2025-12-02 12:06:45.465299 +00:00 | 6afc6772-31bf-4715-a06c-70ff5a4d16af |
     | 6e4f4d92-f72f-4a1d-9ef2-487fcb6d4b10 | 7731 | FORHÅNDSVARSLET\_TILBAKEKREVINGSBEHANDLING | 2025-12-02 12:07:21.113051 +00:00 | 4319ab24-9226-4407-91f1-b26e3fbc0746 |
     | 9f5a548e-c997-46ee-bf86-24daa83894e1 | 7732 | AVBRUTT\_TILBAKEKREVINGSBEHANDLING | 2025-12-02 12:08:30.132999 +00:00 | 6e4f4d92-f72f-4a1d-9ef2-487fcb6d4b10 |
     * I dette caset så skal man ikke generere dokumenter for TK men prøver å gjøre det selvom TK er i feil tilstand kreves: [KanForhåndsvarsle] men er i [AvbruttTilbakekrevingsbehandling]
     */
    override fun hentUteståendeSakOgHendelsesIderForKonsumentOgTypeTilbakekreving(
        konsumentId: HendelseskonsumentId,
        hendelsestype: Hendelsestype,
        sx: SessionContext?,
        limit: Int,
    ): Map<UUID, Nel<HendelseId>> {
        return (sx ?: sessionFactory.newSessionContext()).withSession {
            // Mulig det hadde holdt her å se på h.id = senere.tidligerehendelseid i FROM hendelse senere i stedet for behandlingsId
            """
            SELECT h.sakId, h.hendelseId
            FROM hendelse h
            WHERE h.type = :type
              AND NOT EXISTS (
                SELECT 1
                FROM hendelse_konsument hk
                WHERE hk.hendelseId = h.hendelseId
                  AND hk.konsumentId = :konsumentId
              )
              AND NOT EXISTS (
                SELECT 1
                FROM hendelse senere
                WHERE (senere.data ->> 'behandlingsId')::uuid =
                      (h.data ->> 'behandlingsId')::uuid
                  AND senere.hendelsesnummer > h.hendelsesnummer
                  AND senere.type = 'AVBRUTT_TILBAKEKREVINGSBEHANDLING'
              )
            LIMIT :limit
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "type" to hendelsestype.value,
                        "konsumentId" to konsumentId.value,
                        "limit" to limit,
                    ),
                    it,
                ) {
                    it.uuid("sakId") to HendelseId.fromUUID(it.uuid("hendelseId"))
                }
                .groupBy { it.first }
                .mapValues { (_, value) ->
                    value.map { it.second }.toNonEmptyList()
                }
        }
    }

    // denne blir feil dersom det finnes en tk hendelse avbrutt etter FORHÅNDSVARSLET_TILBAKEKREVINGSBEHANDLING siden den bare da søker etter hendeseskonsument av typen GenererDokumentForForhåndsvarselTilbakekrevingKonsument som aldri finnes siden dne ikke skal finnes siden den er avbrutt det må vi ta høyde for

    override fun hentUteståendeSakOgHendelsesIderForKonsumentOgType(
        konsumentId: HendelseskonsumentId,
        hendelsestype: Hendelsestype,
        sx: SessionContext?,
        limit: Int,
    ): Map<UUID, Nel<HendelseId>> {
        return (sx ?: sessionFactory.newSessionContext()).withSession {
            """
            SELECT
                h.sakId, h.hendelseId
            FROM
                hendelse h
            LEFT JOIN hendelse_konsument hk
                ON h.hendelseId = hk.hendelseId AND hk.konsumentId = :konsumentId
            WHERE
                hk.hendelseId IS NULL
                AND h.type = :type
            LIMIT :limit
            """.trimIndent()
                .hentListe(
                    mapOf("type" to hendelsestype.value, "konsumentId" to konsumentId.value, "limit" to limit),
                    it,
                ) {
                    it.uuid("sakId") to HendelseId.fromUUID(it.uuid("hendelseId"))
                }.let {
                    it.groupBy { it.first }
                        .mapValues { (_, value) ->
                            // TODO: SOS: denne vil kaste for alle som kaller denne og logger error, det ønsker vi ikke bedre om konsumentene returner enn å kaste error?
                            value.map { it.second }.toNonEmptyList()
                        }
                }
        }
    }

    override fun hentHendelseIderForKonsumentOgType(
        konsumentId: HendelseskonsumentId,
        hendelsestype: Hendelsestype,
        sessionContext: SessionContext?,
        limit: Int,
    ): Set<HendelseId> {
        return (sessionContext ?: sessionFactory.newSessionContext()).withSession {
            """
            SELECT
                h.hendelsesnummer,
                h.hendelseId
            FROM
                hendelse h
            LEFT JOIN hendelse_konsument hk
                ON h.hendelseId = hk.hendelseId AND hk.konsumentId = :konsumentId
            WHERE
                hk.hendelseId IS NULL
                AND h.type = :type
            ORDER BY h.hendelsesnummer
            LIMIT :limit
            """.trimIndent()
                .hentListe(
                    mapOf("type" to hendelsestype.value, "konsumentId" to konsumentId.value, "limit" to limit),
                    it,
                ) {
                    HendelseId.fromUUID(it.uuid("hendelseId"))
                }.toSet()
        }
    }
}
