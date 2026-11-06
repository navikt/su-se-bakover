package no.nav.su.se.bakover.dokument.infrastructure.client

import dokument.domain.generer.bygger.maler.KontrollnotatPdfInnholdNy
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize
import person.domain.Person

internal fun KontrollnotatPdfInnholdNy.tilPdfgenJson(): String =
    serialize(tilPdfgenData())

private fun KontrollnotatPdfInnholdNy.tilPdfgenData(): KontrollnotatPdfgenData =
    KontrollnotatPdfgenData(
        sakstype = sakstype,
        saksnummer = saksnummer,
        navn = navn,
        dagensDatoOgTidspunkt = dagensDatoOgTidspunkt,
        kontrollnotat = KontrollnotatPdfgenSvar(
            personligOppmøte = personligOppmøte,
            fullmaktOgLegeerklæring = fullmaktOgLegeerklæring,
            originalPass = originalPass,
            gyldigPass = gyldigPass,
            harVærtUtenlands = harVærtUtenlands,
            utenlandsoppholdDatoer = utenlandsoppholdDatoer,
            harPlanerOmUtenlandsreise = harPlanerOmUtenlandsreise,
            planlagteUtenlandsreiseDatoer = planlagteUtenlandsreiseDatoer,
            reiseDokumentasjon = reiseDokumentasjon,
            økonomiskSituasjon = økonomiskSituasjon,
            andreForhold = andreForhold,
            skatteOpplysninger = skatteOpplysninger,
            fritekst = fritekst,
        ),
    )

private data class KontrollnotatPdfgenData(
    val sakstype: Sakstype,
    val saksnummer: Saksnummer,
    val navn: Person.Navn,
    val dagensDatoOgTidspunkt: String,
    val kontrollnotat: KontrollnotatPdfgenSvar,
)

private data class KontrollnotatPdfgenSvar(
    val personligOppmøte: Boolean,
    val fullmaktOgLegeerklæring: Boolean?,
    val originalPass: Boolean,
    val gyldigPass: Boolean,
    val harVærtUtenlands: Boolean,
    val utenlandsoppholdDatoer: List<String>,
    val harPlanerOmUtenlandsreise: Boolean,
    val planlagteUtenlandsreiseDatoer: List<String>,
    val reiseDokumentasjon: Boolean,
    val økonomiskSituasjon: Boolean,
    val andreForhold: Boolean,
    val skatteOpplysninger: Boolean,
    val fritekst: String?,
)
