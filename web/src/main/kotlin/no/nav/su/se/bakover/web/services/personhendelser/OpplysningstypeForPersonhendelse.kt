package no.nav.su.se.bakover.web.services.personhendelser

enum class OpplysningstypeForPersonhendelse(val value: String) {
    DØDSFALL("DOEDSFALL_V1"),
    UTFLYTTING_FRA_NORGE("UTFLYTTING_FRA_NORGE"),
    SIVILSTAND("SIVILSTAND_V1"),
    BOSTEDSADRESSE("BOSTEDSADRESSE_V1"),
    KONTAKTADRESSE("KONTAKTADRESSE_V1"),
    // https://github.com/navikt/pdl/blob/master/libs/contract-pdl-avro/src/main/java/no/nav/person/pdl/leesah/Opplysningstype.java
}
