package no.nav.su.se.bakover.domain.brev

abstract class Brevinnhold {
    abstract fun toJson(): String
    abstract fun pdfTemplate(): PdfTemplate
}
