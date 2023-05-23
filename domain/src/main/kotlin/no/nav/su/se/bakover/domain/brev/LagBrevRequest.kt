package no.nav.su.se.bakover.domain.brev

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.common.extensions.norwegianLocale
import no.nav.su.se.bakover.common.extensions.toBrevformat
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn.Companion.getDistinkteParagrafer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.brev.beregning.tilBrevperiode
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.text.NumberFormat
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * TODO jah: Denne klassen har referanse til BrevInnhold som er en ren JsonDto (som bør bo under infrastructure). Denne bør splittes opp, hvor deler bør bo i service.
 */
interface LagBrevRequest {
    val person: Person
    val pdfInnhold: PdfInnhold
    val dagensDato: LocalDate
    val saksnummer: Saksnummer

    fun tilDokument(
        clock: Clock,
        genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
    ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata>

    fun genererDokument(
        clock: Clock,
        genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
    ): Either<KunneIkkeGenererePdf, Triple<String, ByteArray, String>> {
        return Triple(
            first = pdfInnhold.pdfTemplate.tittel(),
            second = genererPdf(this).getOrElse { return KunneIkkeGenererePdf.left() },
            third = pdfInnhold.toJson(),
        ).right()
    }

    object KunneIkkeGenererePdf {
        override fun toString() = this::class.simpleName!!
    }

    data class InnvilgetVedtak(
        override val person: Person,
        private val beregning: Beregning,
        private val harEktefelle: Boolean,
        private val forventetInntektStørreEnn0: Boolean,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
        private val satsoversikt: Satsoversikt,
        private val sakstype: Sakstype,
    ) : LagBrevRequest {
        override val pdfInnhold = PdfInnhold.InnvilgetVedtak(
            personalia = lagPersonalia(),
            fradato = beregning.periode.fraOgMed.formatMonthYear(),
            tildato = beregning.periode.tilOgMed.formatMonthYear(),
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            harEktefelle = harEktefelle,
            beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = fritekst,
            satsoversikt = satsoversikt,
            sakstype = sakstype,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class AvslagBrevRequest(
        override val person: Person,
        private val avslag: Avslag,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
        private val forventetInntektStørreEnn0: Boolean,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
        private val satsoversikt: Satsoversikt?,
        private val sakstype: Sakstype,
    ) : LagBrevRequest {
        override val pdfInnhold = PdfInnhold.AvslagsPdfInnhold(
            personalia = lagPersonalia(),
            avslagsgrunner = avslag.avslagsgrunner,
            harEktefelle = avslag.harEktefelle,
            halvGrunnbeløp = avslag.halvtGrunnbeløpPerÅr,
            beregningsperioder = avslag.beregning?.let { LagBrevinnholdForBeregning(it).brevInnhold }
                ?: emptyList(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = fritekst,
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            formueVerdier = avslag.formuegrunnlag?.tilFormueForBrev(),
            satsoversikt = satsoversikt,
            sakstype = sakstype,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    sealed interface Opphør {
        val person: Person
        val dagensDato: LocalDate
        val saksnummer: Saksnummer
        val beregning: Beregning
        val forventetInntektStørreEnn0: Boolean
        val harEktefelle: Boolean
        val saksbehandlerNavn: String
        val attestantNavn: String
        val fritekst: String
        val opphørsgrunner: List<Opphørsgrunn>
        val opphørsperiode: Periode
        val avkortingsBeløp: Int?
    }

    data class Opphørsvedtak(
        override val person: Person,
        override val beregning: Beregning,
        override val forventetInntektStørreEnn0: Boolean,
        override val harEktefelle: Boolean,
        override val saksbehandlerNavn: String,
        override val attestantNavn: String,
        override val fritekst: String,
        override val opphørsgrunner: List<Opphørsgrunn>,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
        override val opphørsperiode: Periode,
        override val avkortingsBeløp: Int?,
        private val satsoversikt: Satsoversikt,
        private val halvtGrunnbeløp: Int,
    ) : LagBrevRequest, Opphør {
        override val pdfInnhold = PdfInnhold.Opphørsvedtak(
            personalia = lagPersonalia(),
            opphørsgrunner = opphørsgrunner,
            avslagsparagrafer = opphørsgrunner.getDistinkteParagrafer(),
            harEktefelle = harEktefelle,
            beregningsperioder = if (
                opphørsgrunner.contains(Opphørsgrunn.FOR_HØY_INNTEKT) ||
                opphørsgrunner.contains(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            ) {
                LagBrevinnholdForBeregning(beregning).brevInnhold
            } else {
                emptyList()
            },
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = fritekst,
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            halvGrunnbeløp = halvtGrunnbeløp,
            opphørsperiode = opphørsperiode.tilBrevperiode(),
            avkortingsBeløp = avkortingsBeløp,
            satsoversikt = satsoversikt,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class Forhåndsvarsel(
        override val person: Person,
        private val saksbehandlerNavn: String,
        private val fritekst: String,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
    ) : LagBrevRequest {
        override val pdfInnhold = PdfInnhold.Forhåndsvarsel(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Informasjon.Viktig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class ForhåndsvarselTilbakekreving(
        override val person: Person,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
        private val saksbehandlerNavn: String,
        private val fritekst: String,
        private val bruttoTilbakekreving: Int,
        private val tilbakekreving: Tilbakekreving,
    ) : LagBrevRequest {
        override val pdfInnhold = PdfInnhold.ForhåndsvarselTilbakekreving(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
            bruttoTilbakekreving = NumberFormat.getNumberInstance(norwegianLocale).format(bruttoTilbakekreving),
            periodeStart = tilbakekreving.periodeStart,
            periodeSlutt = tilbakekreving.periodeSlutt,
            tilbakekreving = tilbakekreving.tilbakekrevingavdrag,
            dato = dagensDato.toBrevformat(),
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Informasjon.Viktig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    /**
     * Brev for når en revurdering er forhåndsvarslet
     * hvis revurderingen ikke er forhåndsvarslet, er det ikke noe brev.
     */
    data class AvsluttRevurdering(
        override val person: Person,
        private val saksbehandlerNavn: String,
        private val fritekst: String?,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
    ) : LagBrevRequest {
        override val pdfInnhold: PdfInnhold = PdfInnhold.AvsluttRevurdering(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Informasjon.Annet(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    sealed interface Revurdering {
        val person: Person
        val dagensDato: LocalDate
        val saksnummer: Saksnummer
        val saksbehandlerNavn: String
        val attestantNavn: String
        val revurdertBeregning: Beregning
        val fritekst: String
        val harEktefelle: Boolean
        val forventetInntektStørreEnn0: Boolean
    }

    data class Inntekt(
        override val person: Person,
        override val saksbehandlerNavn: String,
        override val attestantNavn: String,
        override val revurdertBeregning: Beregning,
        override val fritekst: String,
        override val harEktefelle: Boolean,
        override val forventetInntektStørreEnn0: Boolean,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
        private val satsoversikt: Satsoversikt,
    ) : LagBrevRequest, Revurdering {
        override val pdfInnhold = PdfInnhold.RevurderingAvInntekt(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            beregningsperioder = LagBrevinnholdForBeregning(revurdertBeregning).brevInnhold,
            fritekst = fritekst,
            harEktefelle = harEktefelle,
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            satsoversikt = satsoversikt,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class TilbakekrevingAvPenger(
        private val ordinærtRevurderingBrev: Inntekt,
        private val tilbakekreving: Tilbakekreving,
        private val satsoversikt: Satsoversikt,
    ) : LagBrevRequest, Revurdering by ordinærtRevurderingBrev {
        override val pdfInnhold: PdfInnhold = PdfInnhold.RevurderingMedTilbakekrevingAvPenger(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            beregningsperioder = LagBrevinnholdForBeregning(revurdertBeregning).brevInnhold,
            fritekst = fritekst,
            harEktefelle = harEktefelle,
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            tilbakekreving = tilbakekreving.tilbakekrevingavdrag,
            periodeStart = tilbakekreving.periodeStart,
            periodeSlutt = tilbakekreving.periodeSlutt,
            satsoversikt = satsoversikt,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }

        fun erstattBruttoMedNettoFeilutbetaling(netto: Månedsbeløp): TilbakekrevingAvPenger {
            return copy(tilbakekreving = Tilbakekreving(netto.månedbeløp))
        }
    }

    data class InnkallingTilKontrollsamtale(
        override val person: Person,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
    ) : LagBrevRequest {
        override val pdfInnhold = PdfInnhold.InnkallingTilKontrollsamtale(
            personalia = lagPersonalia(),
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Informasjon.Viktig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    sealed class Klage : LagBrevRequest {
        data class Oppretthold(
            override val person: Person,
            override val dagensDato: LocalDate,
            val saksbehandlerNavn: String,
            val fritekst: String,
            val klageDato: LocalDate,
            val vedtaksbrevDato: LocalDate,
            override val saksnummer: Saksnummer,
        ) : Klage() {
            override val pdfInnhold: PdfInnhold = PdfInnhold.Klage.Oppretthold(
                personalia = lagPersonalia(),
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = fritekst,
                klageDato = klageDato.ddMMyyyy(),
                vedtakDato = vedtaksbrevDato.ddMMyyyy(),
                saksnummer = saksnummer.nummer,
            )

            override fun tilDokument(
                clock: Clock,
                genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
            ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
                return genererDokument(clock, genererPdf).map {
                    Dokument.UtenMetadata.Informasjon.Annet(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        tittel = it.first,
                        generertDokument = it.second,
                        generertDokumentJson = it.third,
                    )
                }
            }
        }

        data class Avvist(
            override val person: Person,
            override val dagensDato: LocalDate,
            val saksbehandlerNavn: String,
            val fritekst: String,
            override val saksnummer: Saksnummer,
        ) : Klage() {
            override val pdfInnhold: PdfInnhold = PdfInnhold.Klage.Avvist(
                personalia = lagPersonalia(),
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = fritekst,
                saksnummer = saksnummer.nummer,
            )

            override fun tilDokument(
                clock: Clock,
                genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
            ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
                return genererDokument(clock, genererPdf).map {
                    Dokument.UtenMetadata.Vedtak(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        tittel = it.first,
                        generertDokument = it.second,
                        generertDokumentJson = it.third,
                    )
                }
            }
        }
    }

    data class PåminnelseNyStønadsperiode(
        override val person: Person,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
        val utløpsdato: LocalDate,
        val halvtGrunnbeløp: Int,
    ) : LagBrevRequest {
        override val pdfInnhold = PdfInnhold.PåminnelseNyStønadsperiode(
            personalia = lagPersonalia(),
            utløpsdato = utløpsdato.ddMMyyyy(),
            halvtGrunnbeløp = halvtGrunnbeløp,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
            return genererDokument(clock, genererPdf).map {
                Dokument.UtenMetadata.Informasjon.Viktig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class Fritekst(
        override val person: Person,
        override val dagensDato: LocalDate,
        override val saksnummer: Saksnummer,
        val saksbehandlerNavn: String,
        val brevTittel: String,
        val fritekst: String,
    ) : LagBrevRequest {
        override val pdfInnhold: PdfInnhold = PdfInnhold.Fritekst(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            tittel = brevTittel,
            fritekst = fritekst,
        )

        override fun tilDokument(
            clock: Clock,
            genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>,
        ): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata> {
            return genererDokument(clock, genererPdf).map {
                // på sikt så vil vi kanskje la saksbehandler velge om brevet er viktig eller annet
                Dokument.UtenMetadata.Informasjon.Annet(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }
}

fun LagBrevRequest.lagPersonalia() = this.person.let {
    PdfInnhold.Personalia(
        dato = dagensDato.ddMMyyyy(),
        fødselsnummer = it.ident.fnr,
        fornavn = it.navn.fornavn,
        etternavn = it.navn.etternavn,
        saksnummer = saksnummer.nummer,
    )
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
