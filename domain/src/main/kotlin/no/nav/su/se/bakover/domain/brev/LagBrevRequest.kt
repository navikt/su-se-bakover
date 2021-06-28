package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn.Companion.getDistinkteParagrafer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

interface LagBrevRequest {
    fun getPerson(): Person
    fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold

    data class InnvilgetVedtak(
        private val person: Person,
        private val beregning: Beregning,
        private val satsgrunn: Satsgrunn,
        private val harEktefelle: Boolean,
        private val forventetInntektStørreEnn0: Boolean,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.InnvilgetVedtak {
            return BrevInnhold.InnvilgetVedtak(
                personalia = personalia,
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
        }
    }

    data class AvslagBrevRequest(
        private val person: Person,
        private val avslag: Avslag,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
        private val forventetInntektStørreEnn0: Boolean,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.AvslagsBrevInnhold {
            return BrevInnhold.AvslagsBrevInnhold(
                personalia = personalia,
                avslagsgrunner = avslag.avslagsgrunner,
                harEktefelle = avslag.harEktefelle,
                halvGrunnbeløp = avslag.halvGrunnbeløp.toInt(),
                beregningsperioder = avslag.beregning?.let { LagBrevinnholdForBeregning(it).brevInnhold }
                    ?: emptyList(),
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                sats = avslag.beregning?.getSats()?.name?.lowercase(),
                satsGjeldendeFraDato = avslag.beregning?.getSats()?.datoForSisteEndringAvSats(avslag.beregning.periode.tilOgMed)?.ddMMyyyy(),
                fritekst = fritekst,
                forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            )
        }
    }

    data class Opphørsvedtak(
        private val person: Person,
        private val beregning: Beregning,
        private val forventetInntektStørreEnn0: Boolean,
        private val harEktefelle: Boolean,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
        private val opphørsgrunner: List<Opphørsgrunn>,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.Opphørsvedtak {
            return BrevInnhold.Opphørsvedtak(
                personalia = personalia,
                sats = beregning.getSats().toString().lowercase(),
                satsBeløp = beregning.getSats().månedsbeløpSomHeltall(beregning.periode.tilOgMed),
                satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed).ddMMyyyy(),
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
            )
        }
    }

    data class VedtakIngenEndring(
        private val person: Person,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val beregning: Beregning,
        private val fritekst: String,
        private val harEktefelle: Boolean,
        private val forventetInntektStørreEnn0: Boolean,
        private val gjeldendeMånedsutbetaling: Int,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person

        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
            return BrevInnhold.VedtakIngenEndring(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
                fritekst = fritekst,
                sats = beregning.getSats(),
                satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed).ddMMyyyy(),
                harEktefelle = harEktefelle,
                forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
                gjeldendeMånedsutbetaling = gjeldendeMånedsutbetaling,
            )
        }
    }

    data class Forhåndsvarsel(
        private val person: Person,
        private val saksbehandlerNavn: String,
        private val fritekst: String,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person

        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
            return BrevInnhold.Forhåndsvarsel(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = fritekst,
            )
        }
    }

    sealed class Revurdering : LagBrevRequest {
        data class Inntekt(
            private val person: Person,
            private val saksbehandlerNavn: String,
            private val attestantNavn: String,
            private val revurdertBeregning: Beregning,
            private val fritekst: String,
            private val harEktefelle: Boolean,
            private val forventetInntektStørreEnn0: Boolean,
        ) : Revurdering() {
            override fun getPerson(): Person = person

            override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
                return BrevInnhold.RevurderingAvInntekt(
                    personalia = personalia,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    beregningsperioder = LagBrevinnholdForBeregning(revurdertBeregning).brevInnhold,
                    fritekst = fritekst,
                    sats = revurdertBeregning.getSats(),
                    satsGjeldendeFraDato = revurdertBeregning.getSats().datoForSisteEndringAvSats(revurdertBeregning.periode.tilOgMed).ddMMyyyy(),
                    harEktefelle = harEktefelle,
                    forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
                )
            }
        }
    }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
