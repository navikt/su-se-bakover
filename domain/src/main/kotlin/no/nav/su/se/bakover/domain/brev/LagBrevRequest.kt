package no.nav.su.se.bakover.domain.brev

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.common.norwegianLocale
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toBrevformat
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

interface LagBrevRequest {
    val person: Person
    val brevInnhold: BrevInnhold
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
            first = brevInnhold.brevTemplate.tittel(),
            second = genererPdf(this).getOrElse { return KunneIkkeGenererePdf.left() },
            third = brevInnhold.toJson(),
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
        override val brevInnhold = BrevInnhold.InnvilgetVedtak(
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
        override val brevInnhold = BrevInnhold.AvslagsBrevInnhold(
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
        override val brevInnhold = BrevInnhold.Opphørsvedtak(
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
        override val brevInnhold = BrevInnhold.Forhåndsvarsel(
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
        override val brevInnhold = BrevInnhold.ForhåndsvarselTilbakekreving(
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
        override val brevInnhold: BrevInnhold = BrevInnhold.AvsluttRevurdering(
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
        override val brevInnhold = BrevInnhold.RevurderingAvInntekt(
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
        override val brevInnhold: BrevInnhold = BrevInnhold.RevurderingMedTilbakekrevingAvPenger(
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
        override val brevInnhold = BrevInnhold.InnkallingTilKontrollsamtale(
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
            override val brevInnhold: BrevInnhold = BrevInnhold.Klage.Oppretthold(
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
            override val brevInnhold: BrevInnhold = BrevInnhold.Klage.Avvist(
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
        override val brevInnhold = BrevInnhold.PåminnelseNyStønadsperiode(
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
        override val brevInnhold: BrevInnhold = BrevInnhold.Fritekst(
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
    BrevInnhold.Personalia(
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
