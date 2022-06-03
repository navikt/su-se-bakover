package no.nav.su.se.bakover.domain.dokument

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
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

        sealed class Informasjon : UtenMetadata() {
            data class Viktig(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: ByteArray,
                override val generertDokumentJson: String,
            ) : Informasjon() {
                override fun leggTilMetadata(metadata: Metadata): MedMetadata.Informasjon.Viktig {
                    return MedMetadata.Informasjon.Viktig(this, metadata)
                }
            }

            data class Annet(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: ByteArray,
                override val generertDokumentJson: String,
            ) : Informasjon() {
                override fun leggTilMetadata(metadata: Metadata): MedMetadata.Informasjon.Annet {
                    return MedMetadata.Informasjon.Annet(this, metadata)
                }
            }
        }
    }

    sealed class MedMetadata : Dokument() {
        abstract val metadata: Metadata
        abstract val distribusjonstype: Distribusjonstype
        val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID

        data class Vedtak(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: ByteArray,
            override val generertDokumentJson: String,
            override val metadata: Metadata,
        ) : MedMetadata() {
            override val distribusjonstype = Distribusjonstype.VEDTAK

            constructor(utenMetadata: UtenMetadata, metadata: Metadata) : this(
                id = utenMetadata.id,
                opprettet = utenMetadata.opprettet,
                tittel = utenMetadata.tittel,
                generertDokument = utenMetadata.generertDokument,
                generertDokumentJson = utenMetadata.generertDokumentJson,
                metadata = metadata,
            )
        }

        /**
         * Typen informasjon vil bestemme når på døgnet brevet skal distribueres. Se DokdistFordelingClient
         */
        sealed class Informasjon : MedMetadata() {
            data class Viktig(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: ByteArray,
                override val generertDokumentJson: String,
                override val metadata: Metadata,
            ) : Informasjon() {
                override val distribusjonstype = Distribusjonstype.VIKTIG

                constructor(utenMetadata: UtenMetadata, metadata: Metadata) : this(
                    id = utenMetadata.id,
                    opprettet = utenMetadata.opprettet,
                    tittel = utenMetadata.tittel,
                    generertDokument = utenMetadata.generertDokument,
                    generertDokumentJson = utenMetadata.generertDokumentJson,
                    metadata = metadata,
                )
            }

            data class Annet(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: ByteArray,
                override val generertDokumentJson: String,
                override val metadata: Metadata,
            ) : Informasjon() {
                override val distribusjonstype = Distribusjonstype.ANNET

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
    }

    data class Metadata(
        val sakId: UUID,
        val søknadId: UUID? = null,
        val vedtakId: UUID? = null,
        val revurderingId: UUID? = null,
        val klageId: UUID? = null,
        /**
         * Hvis satt til true, vil det automatisk opprettes en [Dokumentdistribusjon] for dette [Dokument] ved lagring.
         */
        val bestillBrev: Boolean,

        val journalpostId: String? = null,
        val brevbestillingId: String? = null,
    )
}
