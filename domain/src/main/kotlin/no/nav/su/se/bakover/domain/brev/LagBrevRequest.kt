package no.nav.su.se.bakover.domain.brev

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn.Companion.getDistinkteParagrafer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.dokument.Dokument
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

interface LagBrevRequest {
    val person: Person
    val brevInnhold: BrevInnhold
    val dagensDato: LocalDate

    fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata>

    fun genererDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Triple<String, ByteArray, String>> {
        return Triple(
            first = brevInnhold.brevTemplate.tittel(),
            second = genererPdf(this).getOrHandle { return KunneIkkeGenererePdf.left() },
            third = brevInnhold.toJson(),
        ).right()
    }

    object KunneIkkeGenererePdf

    data class InnvilgetVedtak(
        override val person: Person,
        private val beregning: Beregning,
        private val satsgrunn: Satsgrunn,
        private val harEktefelle: Boolean,
        private val forventetInntektStørreEnn0: Boolean,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
        override val dagensDato: LocalDate,
    ) : LagBrevRequest {
        override val brevInnhold = BrevInnhold.InnvilgetVedtak(
            personalia = lagPersonalia(),
            fradato = beregning.periode.fraOgMed.formatMonthYear(),
            tildato = beregning.periode.tilOgMed.formatMonthYear(),
            // TODO CHM 05.05.2021: Wrap sats-tingene i et eget objekt, hent fra beregning?
            sats = beregning.getSats().toString().lowercase(),
            satsGrunn = satsgrunn,
            satsBeløp = beregning.getSats().månedsbeløpSomHeltall(beregning.periode.tilOgMed),
            satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed)
                .ddMMyyyy(),
            // Innvilgede vedtaker har alltid forventet inntekt
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            harEktefelle = harEktefelle,
            beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = fritekst,
        )

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
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
    ) : LagBrevRequest {
        override val brevInnhold = BrevInnhold.AvslagsBrevInnhold(
            personalia = lagPersonalia(),
            avslagsgrunner = avslag.avslagsgrunner,
            harEktefelle = avslag.harEktefelle,
            halvGrunnbeløp = avslag.halvGrunnbeløp.toInt(),
            beregningsperioder = avslag.beregning?.let { LagBrevinnholdForBeregning(it).brevInnhold }
                ?: emptyList(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            sats = avslag.beregning?.getSats()?.name?.lowercase(),
            satsGjeldendeFraDato = avslag.beregning?.getSats()
                ?.datoForSisteEndringAvSats(avslag.beregning.periode.tilOgMed)?.ddMMyyyy(),
            fritekst = fritekst,
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
        )

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class Opphørsvedtak(
        override val person: Person,
        private val beregning: Beregning,
        private val forventetInntektStørreEnn0: Boolean,
        private val harEktefelle: Boolean,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
        private val opphørsgrunner: List<Opphørsgrunn>,
        override val dagensDato: LocalDate,
        private val opphørsdato: LocalDate,
        private val avkortingsBeløp: Int?,
    ) : LagBrevRequest {
        override val brevInnhold = BrevInnhold.Opphørsvedtak(
            personalia = lagPersonalia(),
            sats = beregning.getSats().toString().lowercase(),
            satsBeløp = beregning.getSats().månedsbeløpSomHeltall(beregning.periode.tilOgMed),
            satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed)
                .ddMMyyyy(),
            harEktefelle = harEktefelle,
            beregningsperioder = if (
                opphørsgrunner.contains(Opphørsgrunn.FOR_HØY_INNTEKT) ||
                opphørsgrunner.contains(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            ) LagBrevinnholdForBeregning(beregning).brevInnhold else emptyList(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            halvGrunnbeløp = Grunnbeløp.`0,5G`.påDato(beregning.periode.fraOgMed).toInt(),
            fritekst = fritekst,
            opphørsgrunner = opphørsgrunner,
            avslagsparagrafer = opphørsgrunner.getDistinkteParagrafer(),
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            opphørsdato = opphørsdato.ddMMyyyy(),
            avkortingsBeløp = avkortingsBeløp,
        )

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class VedtakIngenEndring(
        override val person: Person,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val beregning: Beregning,
        private val fritekst: String,
        private val harEktefelle: Boolean,
        private val forventetInntektStørreEnn0: Boolean,
        private val gjeldendeMånedsutbetaling: Int,
        override val dagensDato: LocalDate,
    ) : LagBrevRequest {
        override val brevInnhold = BrevInnhold.VedtakIngenEndring(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
            fritekst = fritekst,
            sats = beregning.getSats(),
            satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed)
                .ddMMyyyy(),
            harEktefelle = harEktefelle,
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            gjeldendeMånedsutbetaling = gjeldendeMånedsutbetaling,
        )

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
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
    ) : LagBrevRequest {
        override val brevInnhold = BrevInnhold.Forhåndsvarsel(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
        )

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Informasjon(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
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
    ) : LagBrevRequest {
        override val brevInnhold: BrevInnhold = BrevInnhold.AvsluttRevurdering(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
        )

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Informasjon(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO: Ta inn clock
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    sealed class Revurdering : LagBrevRequest {
        data class Inntekt(
            override val person: Person,
            private val saksbehandlerNavn: String,
            private val attestantNavn: String,
            private val revurdertBeregning: Beregning,
            private val fritekst: String,
            private val harEktefelle: Boolean,
            private val forventetInntektStørreEnn0: Boolean,
            override val dagensDato: LocalDate,
        ) : Revurdering() {
            override val brevInnhold = BrevInnhold.RevurderingAvInntekt(
                personalia = lagPersonalia(),
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                beregningsperioder = LagBrevinnholdForBeregning(revurdertBeregning).brevInnhold,
                fritekst = fritekst,
                sats = revurdertBeregning.getSats(),
                satsGjeldendeFraDato = revurdertBeregning.getSats()
                    .datoForSisteEndringAvSats(revurdertBeregning.periode.tilOgMed).ddMMyyyy(),
                harEktefelle = harEktefelle,
                forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            )
        }

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Vedtak> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                    tittel = it.first,
                    generertDokument = it.second,
                    generertDokumentJson = it.third,
                )
            }
        }
    }

    data class InnkallingTilKontrollsamtale(
        override val person: Person,
        override val dagensDato: LocalDate,
    ) : LagBrevRequest {
        override val brevInnhold = BrevInnhold.InnkallingTilKontrollsamtale(
            personalia = lagPersonalia(),
        )

        override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
            return genererDokument(genererPdf).map {
                Dokument.UtenMetadata.Informasjon(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
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
            val vedtakDato: LocalDate,
            val saksnummer: Saksnummer,
        ) : Klage() {
            override val brevInnhold: BrevInnhold = BrevInnhold.Klage(
                personalia = lagPersonalia(),
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = fritekst,
                klageDato = klageDato.ddMMyyyy(),
                vedtakDato = vedtakDato.ddMMyyyy(),
                saksnummer = saksnummer.nummer,
            )

            override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<KunneIkkeGenererePdf, ByteArray>): Either<KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
                return genererDokument(genererPdf).map {
                    Dokument.UtenMetadata.Informasjon(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        tittel = it.first,
                        generertDokument = it.second,
                        generertDokumentJson = it.third,
                    )
                }
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
    )
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
