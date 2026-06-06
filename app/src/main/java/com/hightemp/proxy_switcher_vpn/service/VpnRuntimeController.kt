package com.hightemp.proxy_switcher_vpn.service

import com.hightemp.proxy_switcher_vpn.data.local.ProxyEntity
import com.hightemp.proxy_switcher_vpn.proxy.ProxyNetworkResolver
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.utils.AppLogger
import com.hightemp.proxy_switcher_vpn.utils.LogType
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngine
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineCommandResult
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngineStartRequest
import com.hightemp.proxy_switcher_vpn.vpn.routing.VpnRouteSelection
import com.hightemp.proxy_switcher_vpn.vpn.routing.sensitiveValues
import com.hightemp.proxy_switcher_vpn.vpn.singbox.SingBoxConfigGenerator
import com.hightemp.proxy_switcher_vpn.vpn.singbox.SingBoxProxyEndpoint
import javax.inject.Inject

sealed interface VpnRuntimeControllerResult {
    data object Success : VpnRuntimeControllerResult
    data class Failure(val message: String) : VpnRuntimeControllerResult
}

class VpnRuntimeController @Inject constructor(
    private val engine: VpnEngine,
    private val proxyTester: ProxyReachabilityTester,
    private val proxyNetworkResolver: ProxyNetworkResolver
) {
    private val configGenerator = SingBoxConfigGenerator()

    suspend fun start(routeSelection: VpnRouteSelection): VpnRuntimeControllerResult {
        AppLogger.info(
            message = "VPN start requested for ${routeSelection.displayLabel}.",
            type = LogType.VPN
        )

        if (routeSelection == VpnRouteSelection.Direct) {
            return startDirect()
        }

        val selectedProxy = (routeSelection as VpnRouteSelection.Proxy).proxy
        val probe = runCatching {
            proxyTester.test(selectedProxy)
        }.getOrElse {
            return failClosed(
                message = "Selected upstream proxy failed: Proxy test failed.",
                selectedProxy = selectedProxy
            )
        }
        if (!probe.success) {
            return failClosed(
                message = "Selected upstream proxy failed: ${probe.message}",
                selectedProxy = selectedProxy
            )
        }

        val proxyEndpoint = probe.resolvedProxyHost
            ?.let { resolvedProxyHost ->
                SingBoxProxyEndpoint.resolved(
                    selectedProxy = selectedProxy,
                    resolvedServer = resolvedProxyHost
                )
            }
            ?: runCatching {
                val target = proxyNetworkResolver.resolve(
                    host = selectedProxy.host,
                    port = selectedProxy.port,
                    preferNonVpnNetwork = false
                )
                check(!target.socketAddress.isUnresolved) {
                    "Proxy host could not be resolved."
                }
                SingBoxProxyEndpoint.resolved(
                    selectedProxy = selectedProxy,
                    resolvedServer = target.serverHost
                )
            }.getOrElse {
                return failClosed(
                    message = "Selected upstream proxy failed: Proxy host bootstrap resolution failed.",
                    selectedProxy = selectedProxy
                )
            }

        val generatedConfig = runCatching {
            configGenerator.generate(
                selectedProxy = selectedProxy,
                proxyEndpoint = proxyEndpoint
            )
        }.getOrElse {
            return failClosed(
                message = "Failed to generate VPN config.",
                selectedProxy = selectedProxy
            )
        }

        val startResult = runCatching {
            engine.start(
                VpnEngineStartRequest(
                    routeSelection = routeSelection,
                    generatedConfig = generatedConfig.json
                )
            )
        }.getOrElse {
            return failClosed(
                message = "VPN engine failed to start.",
                selectedProxy = selectedProxy
            )
        }

        return when (startResult) {
            VpnEngineCommandResult.Success -> {
                AppLogger.info(
                    message = "VPN engine started.",
                    type = LogType.VPN
                )
                VpnRuntimeControllerResult.Success
            }
            is VpnEngineCommandResult.Failure -> {
                failClosed(
                    message = startResult.message,
                    selectedProxy = selectedProxy
                )
            }
        }
    }

    private suspend fun startDirect(): VpnRuntimeControllerResult {
        val generatedConfig = runCatching {
            configGenerator.generateDirect()
        }.getOrElse {
            return failClosed(
                message = "Failed to generate direct VPN config.",
                routeSelection = VpnRouteSelection.Direct
            )
        }

        val startResult = runCatching {
            engine.start(
                VpnEngineStartRequest(
                    routeSelection = VpnRouteSelection.Direct,
                    generatedConfig = generatedConfig.json
                )
            )
        }.getOrElse {
            return failClosed(
                message = "Direct VPN engine failed to start.",
                routeSelection = VpnRouteSelection.Direct
            )
        }

        return when (startResult) {
            VpnEngineCommandResult.Success -> {
                AppLogger.info(
                    message = "Direct VPN engine started.",
                    type = LogType.VPN
                )
                VpnRuntimeControllerResult.Success
            }
            is VpnEngineCommandResult.Failure -> {
                failClosed(
                    message = startResult.message,
                    routeSelection = VpnRouteSelection.Direct
                )
            }
        }
    }

    suspend fun stop(): VpnRuntimeControllerResult {
        return when (val result = engine.stop()) {
            VpnEngineCommandResult.Success -> {
                AppLogger.info(
                    message = "VPN engine stopped.",
                    type = LogType.VPN
                )
                VpnRuntimeControllerResult.Success
            }
            is VpnEngineCommandResult.Failure -> {
                AppLogger.error(
                    message = "VPN engine stop failed: ${result.message}",
                    type = LogType.VPN
                )
                VpnRuntimeControllerResult.Failure(result.message)
            }
        }
    }

    private suspend fun failClosed(
        message: String,
        selectedProxy: ProxyEntity
    ): VpnRuntimeControllerResult.Failure {
        return failClosed(
            message = message,
            routeSelection = VpnRouteSelection.Proxy(selectedProxy)
        )
    }

    private suspend fun failClosed(
        message: String,
        routeSelection: VpnRouteSelection
    ): VpnRuntimeControllerResult.Failure {
        engine.stop()
        AppLogger.error(
            message = "VPN stopped fail-closed: $message",
            type = LogType.PROXY,
            sensitiveValues = routeSelection.sensitiveValues()
        )
        return VpnRuntimeControllerResult.Failure(message)
    }
}
