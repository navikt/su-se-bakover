package no.nav.su.se.bakover.common.infrastructure.auth

import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

object AuthStubCommonConfig {
    private val keypair = generateKeypair()
    const val keyId = "key-1234"
    const val issuer = "localhost"
    val public = keypair.first
    val private = keypair.second

    private fun generateKeypair(): Pair<RSAPublicKey, RSAPrivateKey> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keypair = keyPairGenerator.genKeyPair()
        return keypair.public as RSAPublicKey to keypair.private as RSAPrivateKey
    }
}
