package no.nav.su.se.bakover.domain.behandling

interface StatusovergangVisitor {
    fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Opprettet) {
        throw UgyldigStatusovergangException()
    }

    fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Vilkårsvurdert) {
        throw UgyldigStatusovergangException()
    }

    fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Beregnet) {
        throw UgyldigStatusovergangException()
    }

    fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Simulert) {
        throw UgyldigStatusovergangException()
    }

    fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.TilAttestering) {
        throw UgyldigStatusovergangException()
    }

    fun visit(saksbehandling: Saksbehandling.Søknadsbehandling.Attestert) {
        throw UgyldigStatusovergangException()
    }

    class UgyldigStatusovergangException : RuntimeException("Ugyldig statusovergang")
}
