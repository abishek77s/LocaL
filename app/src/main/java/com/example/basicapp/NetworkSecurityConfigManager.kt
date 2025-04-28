package com.example.basicapp

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class NetworkSecurityConfigManager(private val context: Context) {
    fun updateNetworkSecurityConfig(ipAddress: String) {
        val configContent = """<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">$ipAddress</domain>
    </domain-config>
</network-security-config>"""

        try {
            val configFile = File(context.filesDir, "network_security_config.xml")
            FileOutputStream(configFile).use { output ->
                output.write(configContent.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}