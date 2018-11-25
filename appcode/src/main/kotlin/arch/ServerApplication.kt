package arch

import config.ServerAppConfig
import fi.iki.elonen.NanoHTTPD
import logging.Logger
import java.io.IOException
import repo.MockRepository
import server.MockServer

class ServerApplication(var appConfig: ServerAppConfig, var logger: Logger) {
    fun onStart() {
        val mockRepository = MockRepository(this, appConfig, logger)

        val mockPackName = appConfig.mockPackName
        mockRepository.setMockPack(mockPackName)

        val mockServer = MockServer(this, appConfig, logger, mockRepository)
        try {
            mockServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            logger.info("Mock server started on port ${appConfig.port}")

        } catch (e: IOException) {
            logger.info("Failed to onStart mock configuration server on port " + appConfig.port + ": " + e.message)
            logger.info(e.message!!)
            throw RuntimeException("Failed to onStart mock configuration server on port ${appConfig.port}")
        }
    }

}
