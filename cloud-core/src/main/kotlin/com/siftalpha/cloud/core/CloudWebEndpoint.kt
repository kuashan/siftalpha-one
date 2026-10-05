package com.siftalpha.cloud.core

data class CloudWebEndpoint(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String = "/",
) {
    init {
        validate()
    }

    fun validate(): CloudWebEndpoint {
        require(scheme == "http" || scheme == "https") { "Web endpoint scheme is not allowed" }
        require(isPrivateIpv4(host)) { "Web endpoint host must be private IPv4" }
        require(port in 1..65_535) { "Web endpoint port is invalid" }
        require(path.startsWith("/") && !path.split('/').contains("..")) { "Web endpoint path is invalid" }
        return this
    }

    fun url(): String = "$scheme://$host:$port${if (path == "/") "/" else "/${path.trimStart('/')}"}"

    private fun isPrivateIpv4(value: String): Boolean {
        val octets = value.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }
}
