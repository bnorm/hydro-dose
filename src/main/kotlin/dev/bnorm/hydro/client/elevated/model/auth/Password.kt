package dev.bnorm.hydro.client.elevated.model.auth

import kotlinx.serialization.Serializable

@RequiresOptIn
annotation class PasswordUsage

@Serializable
@JvmInline
value class Password(
    @property:PasswordUsage
    val value: String
) {
    override fun toString(): String = "*****"
}
