package no.nav.su.se.bakover.domain.dokument

import no.nav.su.se.bakover.common.Tidspunkt
import java.util.UUID

sealed class Dokument {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val tittel: String
    abstract val generertDokument: ByteArray

    /**
     * Json-representasjon av data som ble benyttet for opprettelsen av [generertDokument]
     */
    abstract val generertDokumentJson: String

    sealed class UtenMetadata : Dokument() {

        abstract fun leggTilMetadata(metadata: Metadata): MedMetadata

        data class Vedtak(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: ByteArray,
            override val generertDokumentJson: String,
        ) : UtenMetadata() {
            override fun leggTilMetadata(metadata: Metadata): MedMetadata.Vedtak {
                return MedMetadata.Vedtak(this, metadata)
            }
        }

        data class Informasjon(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: ByteArray,
            override val generertDokumentJson: String,
        ) : UtenMetadata() {
            override fun leggTilMetadata(metadata: Metadata): MedMetadata.Informasjon {
                return MedMetadata.Informasjon(this, metadata)
            }
        }
    }

    sealed class MedMetadata : Dokument() {
        abstract val metadata: Metadata

        data class Vedtak(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: ByteArray,
            override val generertDokumentJson: String,
            override val metadata: Metadata,
        ) : MedMetadata() {
            constructor(utenMetadata: UtenMetadata, metadata: Metadata) : this(
                id = utenMetadata.id,
                opprettet = utenMetadata.opprettet,
                tittel = utenMetadata.tittel,
                generertDokument = utenMetadata.generertDokument,
                generertDokumentJson = utenMetadata.generertDokumentJson,
                metadata = metadata,
            )
        }

        data class Informasjon(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: ByteArray,
            override val generertDokumentJson: String,
            override val metadata: Metadata,
        ) : MedMetadata() {
            constructor(utenMetadata: UtenMetadata, metadata: Metadata) : this(
                id = utenMetadata.id,
                opprettet = utenMetadata.opprettet,
                tittel = utenMetadata.tittel,
                generertDokument = utenMetadata.generertDokument,
                generertDokumentJson = utenMetadata.generertDokumentJson,
                metadata = metadata,
            )
        }
    }

    data class Metadata(
        val sakId: UUID,
        val søknadId: UUID? = null,
        val vedtakId: UUID? = null,
        val revurderingId: UUID? = null,
        /**
         * Hvis satt til true, vil det automatisk opprettes en [Dokumentdistribusjon] for dette [Dokument] ved lagring.
         */
        val bestillBrev: Boolean,

        val journalpostId: String? = null,
        val brevbestillingId: String? = null,
    )
}
