package no.nav.su.se.bakover.domain

/**
 * TODO John Andre Hestad: Det skal være mulig å bygge en testJar og importere denne fra gradle.
 */
object VedtakInnholdTestdataBuilder {

    fun build(): VedtakInnhold {
        return VedtakInnhold(
            dato = "01.01.2020",
            fødselsnummer = Fnr("12345678901"),
            fornavn = "Tore",
            etternavn = "Strømøy",
            adresse = "en Adresse",
            husnummer = "4C",
            bruksenhet = "H102",
            postnummer = "0186",
            poststed = "Oslo",
            månedsbeløp = 100,
            fradato = "01.01.2020",
            tildato = "01.01.2020",
            sats = "100",
            satsbeløp = 100,
            satsGrunn = "en sats grunn",
            redusertStønadStatus = true,
            redusertStønadGrunn = "en redusert stønadsgrunn",
            fradrag = emptyList(),
            fradragSum = 0,
            status = Behandling.BehandlingsStatus.TIL_ATTESTERING,
            avslagsgrunn = Avslagsgrunn.FLYKTNING,
            avslagsgrunnBeskrivelse = AvslagsgrunnBeskrivelseFlagg.FORMUE,
            halvGrunnbeløp = 50
        )
    }
}
