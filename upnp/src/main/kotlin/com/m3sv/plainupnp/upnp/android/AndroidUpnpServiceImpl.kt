package com.m3sv.plainupnp.upnp.android

import android.app.Application
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.PlainUpnpServiceConfiguration
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceConfiguration
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.controlpoint.ControlPointImpl
import org.fourthline.cling.protocol.ProtocolFactory
import org.fourthline.cling.protocol.ProtocolFactoryImpl
import org.fourthline.cling.registry.Registry
import org.fourthline.cling.registry.RegistryImpl
import org.fourthline.cling.transport.Router
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidUpnpServiceImpl @Inject constructor(
    application: Application,
    private val logger: Logger,
) : UpnpService {
    private val _configuration = PlainUpnpServiceConfiguration()
    private val _protocolFactory: ProtocolFactory by lazy { ProtocolFactoryImpl(this) }
    private val _registry: Registry by lazy { RegistryImpl(this) }
    private val _controlPoint: ControlPoint by lazy { ControlPointImpl(_configuration, _protocolFactory, _registry) }
    private val router: Router = AndroidRouter(_configuration, _protocolFactory, application)

    override fun getProtocolFactory(): ProtocolFactory = _protocolFactory

    fun start() {
        runCatching {
            router.enable()
        }.onFailure { logger.e("Failed to start router") }
    }

    override fun getConfiguration(): UpnpServiceConfiguration = _configuration

    override fun getControlPoint(): ControlPoint = _controlPoint

    override fun getRegistry(): Registry = _registry

    override fun getRouter(): Router = router

    @Synchronized
    override fun shutdown() {
        Timber.i(">>> Shutting down UPnP service...")
        registry.shutdown()
        runCatching {
            getRouter().shutdown()
        }.onFailure { logger.e("Failed to shutdown router") }
        configuration.shutdown()
        Timber.i("<<< UPnP service shutdown completed")
    }
}

