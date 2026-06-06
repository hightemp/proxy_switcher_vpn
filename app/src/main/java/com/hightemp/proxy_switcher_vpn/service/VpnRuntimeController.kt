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

    suspend fun start(
        routeSelection: VpnRouteSelection,
        stopEngineOnFailure: Boolean = true
    ): VpnRuntimeControllerResult {
        AppLogger.info(
            message = "VPN start requested for ${routeSelection.displayLabel}.",
            type = LogType.VPN
        )

        if (routeSelection == VpnRouteSelection.Direct) {
            return startDirect(stopEngineOnFailure = stopEngineOnFailure)
        }

        val selectedProxy = (routeSelection as VpnRouteSelection.Proxy).proxy
        val probe = runCatching {
            proxyTester.test(selectedProxy)
        }.getOrElse {
            return failClosed(
                message = "Selected upstream proxy failed: Proxy test failed.",
                selectedProxy = selectedProxy,
                stopEngineOnFailure = stopEngineOnFailure
            )
        }
        if (!probe.success) {
            return failClosed(
                message = "Selected upstream proxy failed: ${probe.message}",
                selectedProxy = selectedProxy,
                stopEngineOnFailure = stopEngineOnFailure
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
                    selectedProxy = selectedProxy,
                    stopEngineOnFailure = stopEngineOnFailure
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
                selectedProxy = selectedProxy,
                stopEngineOnFailure = stopEngineOnFailure
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
                selectedProxy = selectedProxy,
                stopEngineOnFailure = stopEngineOnFailure
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
                    selectedProxy = selectedProxy,
                    stopEngineOnFailure = stopEngineOnFailure
                )
            }
        }
    }

    private suspend fun startDirect(
        stopEngineOnFailure: Boolean
    ): VpnRuntimeControllerResult {
        val generatedConfig = runCatching {
            configGenerator.generateDirect()
        }.getOrElse {
            return failClosed(
                message = "Failed to generate direct VPN config.",
                routeSelection = VpnRouteSelection.Direct,
                stopEngineOnFailure = stopEngineOnFailure
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
                routeSelection = VpnRouteSelection.Direct,
                stopEngineOnFailure = stopEngineOnFailure
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
                    routeSelection = VpnRouteSelection.Direct,
                    stopEngineOnFailure = stopEngineOnFailure
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
        selectedProxy: ProxyEntity,
        stopEngineOnFailure: Boolean
    ): VpnRuntimeControllerResult.Failure {
        return failClosed(
            message = message,
            routeSelection = VpnRouteSelection.Proxy(selectedProxy),
            stopEngineOnFailure = stopEngineOnFailure
        )
    }

    private suspend fun failClosed(
        message: String,
        routeSelection: VpnRouteSelection,
        stopEngineOnFailure: Boolean
    ): VpnRuntimeControllerResult.Failure {
        if (stopEngineOnFailure) {
            engine.stop()
            AppLogger.error(
                message = "VPN stopped fail-closed: $message",
                type = LogType.PROXY,
                sensitiveValues = routeSelection.sensitiveValues()
            )
        } else {
            AppLogger.warning(
                message = "VPN start/reconnect attempt failed: $message",
                type = LogType.PROXY,
                sensitiveValues = routeSelection.sensitiveValues()
            )
        }
        return VpnRuntimeControllerResult.Failure(message)
    }
}
