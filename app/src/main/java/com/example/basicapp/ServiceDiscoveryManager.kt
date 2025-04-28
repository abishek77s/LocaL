package com.example.basicapp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int
)

class ServiceDiscoveryManager(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val networkSecurityManager = NetworkSecurityConfigManager(context)

    private val _discoveredServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredServices: StateFlow<List<DiscoveredService>> = _discoveredServices.asStateFlow()

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            println("Discovery started for type: $serviceType")
        }
        @Suppress("DEPRECATION")
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            println("Service Found: ${serviceInfo.serviceName}")

            try {
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        println("Resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                        val hostAddress = resolvedServiceInfo.host?.hostAddress ?: return
                        val service = DiscoveredService(
                            name = resolvedServiceInfo.serviceName,
                            host = hostAddress,
                            port = resolvedServiceInfo.port
                        )

                        networkSecurityManager.updateNetworkSecurityConfig(hostAddress)

                        val currentList = _discoveredServices.value.toMutableList()
                        if (!currentList.any { it.host == hostAddress }) {
                            currentList.add(service)
                            _discoveredServices.value = currentList
                        }
                    }
                }

                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, resolveListener)
            } catch (e: Exception) {
                println("Failed to resolve service: ${e.message}")
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val currentList = _discoveredServices.value.toMutableList()
            currentList.removeAll { it.name == serviceInfo.serviceName }
            _discoveredServices.value = currentList
        }

        override fun onDiscoveryStopped(serviceType: String) {
            println("Discovery stopped for type: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            println("Start discovery failed: $errorCode")
            try {
                nsdManager.stopServiceDiscovery(this)
            } catch (e: Exception) {
                println("Failed to stop discovery after failure: ${e.message}")
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            println("Stop discovery failed: $errorCode")
            try {
                nsdManager.stopServiceDiscovery(this)
            } catch (e: Exception) {
                println("Failed to stop discovery after failure: ${e.message}")
            }
        }
    }

    fun startDiscovery(serviceType: String = "_http._tcp.") {
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            println("Failed to start discovery: ${e.message}")
        }
    }


    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            println("Failed to stop discovery: ${e.message}")
        }
    }
}
