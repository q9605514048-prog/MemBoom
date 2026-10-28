package local.accountvault.android

data class DeviceRegistration(val deviceId: String, val deviceToken: String) {
    companion object {
        fun parse(json: String): DeviceRegistration {
            val id = Regex("\\\"deviceId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)?.groupValues?.get(1)
            val token = Regex("\\\"deviceToken\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)?.groupValues?.get(1)
            require(!id.isNullOrBlank() && !token.isNullOrBlank()) { "Invalid device registration response" }
            return DeviceRegistration(id, token)
        }
    }
}
