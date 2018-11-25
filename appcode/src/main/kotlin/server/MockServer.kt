package server

import arch.ServerApplication
import config.ServerAppConfig
import fi.iki.elonen.NanoHTTPD
import logging.Logger
import repo.MockRepository
import repo.MockRepositoryEntry
import java.io.IOException
import java.util.*

class MockServer(application: ServerApplication, appConfig: ServerAppConfig, logger: Logger, mockRepository: MockRepository) : NanoHTTPD(appConfig.port) {
    val appContext = application
    val appConfig = appConfig
    val logger = logger
    val mockRepo = mockRepository
    val UTF8 = "text/xml; charset=utf-8"

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        logger.info("Mock server: " + session.method.toString() + " - " + session.uri)

        val method = session.method
        val uri = session.uri

        var responseText: String? = ""

        // Find a packaged response that matches the input parameters...
        val entry = MockRepositoryEntry()
        entry.request.host = "http://127.0.0.1"
        entry.request.method = session.method.toString()
        entry.request.headers.putAll(session.headers)
        entry.request.uri = session.uri
        entry.request.queryString = session.queryParameterString


        if (entry.request.queryString == null) entry.request.queryString = ""

        val bodyParts = TreeMap<String, String>()
        try {
            session.parseBody(bodyParts)
            entry.request.body = bodyParts

        } catch (e: IOException) {
            logger.debug("Failed parsing original request: $e")
        } catch (e: NanoHTTPD.ResponseException) {
            logger.debug("Failed parsing original request: $e")
        }

        var cacheEntry: MockRepositoryEntry? = null
        synchronized(mockRepo) {
            try {
                cacheEntry = mockRepo.get(entry)
            } catch (e: Exception) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, UTF8, e.message)
            }

        }

        if (cacheEntry != null && cacheEntry!!.response != null && cacheEntry!!.response.responseDelay != null) {
            // Delay handling of this response for this amount of time...
            val responseDelay = cacheEntry!!.response.responseDelay!!
            try {
                logger.info("Matching mock has a simulated delay. Delaying for $responseDelay ms")
                Thread.sleep(responseDelay.toLong())
            } catch (e: InterruptedException) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, UTF8, "Timeout.")
            }

        }

        if (cacheEntry != null && cacheEntry!!.response != null) {
            responseText = cacheEntry!!.response.body

            val resp = NanoHTTPD.newFixedLengthResponse(intToResponseCode(cacheEntry!!.response.code), UTF8, responseText)
            for (str in cacheEntry!!.response.headers.keys) {
                resp.addHeader(str, cacheEntry!!.response.headers[str])
            }

            return resp
        } else {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_IMPLEMENTED, UTF8, "Response not mocked\r\n\r\n")
        }
    }


    private fun intToResponseCode(code: Int): Response.IStatus {
        when (code) {
            101 -> return NanoHTTPD.Response.Status.SWITCH_PROTOCOL
            200 -> return NanoHTTPD.Response.Status.OK
            201 -> return NanoHTTPD.Response.Status.CREATED
            202 -> return NanoHTTPD.Response.Status.ACCEPTED
            204 -> return NanoHTTPD.Response.Status.NO_CONTENT
            206 -> return NanoHTTPD.Response.Status.PARTIAL_CONTENT
            301 -> return NanoHTTPD.Response.Status.REDIRECT
            304 -> return NanoHTTPD.Response.Status.NOT_MODIFIED
            400 -> return NanoHTTPD.Response.Status.BAD_REQUEST
            401 -> return NanoHTTPD.Response.Status.UNAUTHORIZED
            403 -> return NanoHTTPD.Response.Status.FORBIDDEN
            404 -> return NanoHTTPD.Response.Status.NOT_FOUND
            405 -> return NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED
            406 -> return NanoHTTPD.Response.Status.NOT_ACCEPTABLE
            408 -> return NanoHTTPD.Response.Status.REQUEST_TIMEOUT
            409 -> return NanoHTTPD.Response.Status.CONFLICT
            416 -> return NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE
            500 -> return NanoHTTPD.Response.Status.INTERNAL_ERROR
            501 -> return NanoHTTPD.Response.Status.NOT_IMPLEMENTED
            505 -> return NanoHTTPD.Response.Status.UNSUPPORTED_HTTP_VERSION
            else -> return NanoHTTPD.Response.Status.NOT_IMPLEMENTED
        }
    }
}
