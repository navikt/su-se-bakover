package no.nav.su.se.bakover.web.routes.regulering

data class AutomatiskReguleringBody(val fraOgMedMåned: String, val csv: String? = null)
