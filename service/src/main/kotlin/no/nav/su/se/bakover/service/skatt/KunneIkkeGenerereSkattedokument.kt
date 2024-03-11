package no.nav.su.se.bakover.service.skatt

sealed interface KunneIkkeGenerereSkattedokument {

    data object FeilVedGenereringAvDokument : KunneIkkeGenerereSkattedokument

    data object SkattegrunnlagErIkkeHentetForÅGenereDokument : KunneIkkeGenerereSkattedokument
}
