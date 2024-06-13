package dokument.domain

import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

sealed interface Dokument {
    val id: UUID
    val opprettet: Tidspunkt
    val tittel: String
    val generertDokument: PdfA

    /**
     * Json-representasjon av data som ble benyttet for opprettelsen av [generertDokument]
     */
    val generertDokumentJson: String

    fun erJournalført() = this is MedMetadata && metadata.journalpostId != null
    fun erBrevBestilt() = this is MedMetadata && metadata.brevbestillingId != null

    sealed interface UtenMetadata : Dokument {

        fun leggTilMetadata(metadata: Metadata, distribueringsadresse: Distribueringsadresse?): MedMetadata

        data class Vedtak(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: PdfA,
            override val generertDokumentJson: String,
        ) : UtenMetadata {
            override fun leggTilMetadata(
                metadata: Metadata,
                distribueringsadresse: Distribueringsadresse?,
            ): MedMetadata.Vedtak {
                return MedMetadata.Vedtak(this, metadata, distribueringsadresse)
            }
        }

        sealed interface Informasjon : UtenMetadata {
            data class Viktig(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
            ) : Informasjon {
                override fun leggTilMetadata(
                    metadata: Metadata,
                    distribueringsadresse: Distribueringsadresse?,
                ): MedMetadata.Informasjon.Viktig {
                    return MedMetadata.Informasjon.Viktig(this, metadata, distribueringsadresse)
                }
            }

            data class Annet(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
            ) : Informasjon {
                override fun leggTilMetadata(
                    metadata: Metadata,
                    distribueringsadresse: Distribueringsadresse?,
                ): MedMetadata.Informasjon.Annet {
                    return MedMetadata.Informasjon.Annet(this, metadata, distribueringsadresse)
                }
            }
        }
    }

    /**
     * TODO jah: I visse tilfeller journalfører vi uten å sende brev. F.eks. skattemelding (notat). I de tilfellene er det rart å at vi knytter distribusjonsdata til dokumentet. i.e. distribusjonstype, distribusjonstidspunkt og brevbestillingId.
     */
    sealed interface MedMetadata : Dokument {
        val metadata: Metadata
        val distribusjonstype: Distribusjonstype
        val distribusjonstidspunkt get() = Distribusjonstidspunkt.KJERNETID

        /**
         * Spesifisering av adressen som brevet skal sendes til. Hvis denne er lagt på, vil brevet alltid bli sendt ut av dokdist.
         * Ved null, vil dokdist finne adressen til mottakeren selv, og sender brevet der.
         *
         * Eksempel der man vil legge til adresse er dersom brukeren er død, og man vil sende brevet til en annen person/adresse slik at brevet kommer frem uten at dokdist returnerer 410
         *
         * Lagt til 10.06.2024
         */
        val distribueringsadresse: Distribueringsadresse?

        val journalpostId: String? get() = metadata.journalpostId
        val brevbestillingId: String? get() = metadata.brevbestillingId

        data class Vedtak(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt,
            override val tittel: String,
            override val generertDokument: PdfA,
            override val generertDokumentJson: String,
            override val distribueringsadresse: Distribueringsadresse?,
            override val metadata: Metadata,
        ) : MedMetadata {
            override val distribusjonstype = Distribusjonstype.VEDTAK

            constructor(
                utenMetadata: UtenMetadata.Vedtak,
                metadata: Metadata,
                distribueringsadresse: Distribueringsadresse?,
            ) : this(
                id = utenMetadata.id,
                opprettet = utenMetadata.opprettet,
                tittel = utenMetadata.tittel,
                generertDokument = utenMetadata.generertDokument,
                generertDokumentJson = utenMetadata.generertDokumentJson,
                distribueringsadresse = distribueringsadresse,
                metadata = metadata,
            )
        }

        /**
         * Typen informasjon vil bestemme når på døgnet brevet skal distribueres. Se DokdistFordelingClient
         */
        sealed interface Informasjon : MedMetadata {
            data class Viktig(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
                override val distribueringsadresse: Distribueringsadresse?,
                override val metadata: Metadata,
            ) : Informasjon {
                override val distribusjonstype = Distribusjonstype.VIKTIG

                constructor(
                    utenMetadata: UtenMetadata.Informasjon.Viktig,
                    metadata: Metadata,
                    distribueringsadresse: Distribueringsadresse?,
                ) : this(
                    id = utenMetadata.id,
                    opprettet = utenMetadata.opprettet,
                    tittel = utenMetadata.tittel,
                    generertDokument = utenMetadata.generertDokument,
                    generertDokumentJson = utenMetadata.generertDokumentJson,
                    distribueringsadresse = distribueringsadresse,
                    metadata = metadata,
                )
            }

            data class Annet(
                override val id: UUID = UUID.randomUUID(),
                override val opprettet: Tidspunkt,
                override val tittel: String,
                override val generertDokument: PdfA,
                override val generertDokumentJson: String,
                override val distribueringsadresse: Distribueringsadresse?,
                override val metadata: Metadata,
            ) : Informasjon {
                override val distribusjonstype = Distribusjonstype.ANNET

                constructor(
                    utenMetadata: UtenMetadata.Informasjon.Annet,
                    metadata: Metadata,
                    distribueringsadresse: Distribueringsadresse?,
                ) : this(
                    id = utenMetadata.id,
                    opprettet = utenMetadata.opprettet,
                    tittel = utenMetadata.tittel,
                    generertDokument = utenMetadata.generertDokument,
                    generertDokumentJson = utenMetadata.generertDokumentJson,
                    distribueringsadresse = distribueringsadresse,
                    metadata = metadata,
                )
            }
        }
    }

    /**
     * Du skal bruke en, og bare en knyttning til vedtak eller behandling
     * Dersom du har behov for å knytte et dokument til flere, da kreves det en del omskrivning
     */
    data class Metadata(
        val sakId: UUID,
        /**
         * Denne er for selve søknaden, og ikke behandlingen.
         * Dokumenter for behandlingen baserer seg bare på utkast. Deretter når dem gjøres om til vedtak, brukes vedtakId
         */
        val søknadId: UUID? = null,
        val vedtakId: UUID? = null,
        val revurderingId: UUID? = null,
        val klageId: UUID? = null,
        val tilbakekrevingsbehandlingId: UUID? = null,
        // TODO jah: Sjekk hvorfor vi ikke bruker JournalpostId her
        val journalpostId: String? = null,
        // TODO jah: Sjekk hvorfor vi ikke bruker BrevbestillingId her
        val brevbestillingId: String? = null,
    )
}
