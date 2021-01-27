package no.nav.su.se.bakover.domain.behandling

interface StatusovergangVisitor {
    fun visit(søknadsbehandling: Søknadsbehandling.Opprettet) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Beregnet) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Attestert.Underkjent) {
        throw UgyldigStatusovergangException()
    }

    fun visit(søknadsbehandling: Søknadsbehandling.Attestert) {
        throw UgyldigStatusovergangException()
    }

    class UgyldigStatusovergangException : RuntimeException("Ugyldig statusovergang")
}
