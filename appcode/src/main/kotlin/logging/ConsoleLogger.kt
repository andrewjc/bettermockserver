package logging

import mu.KotlinLogging

class ConsoleLogger() : Logger {
    private val logger = KotlinLogging.logger {}

    override fun debug(s: String) {
        logger.debug { s }
    }

    override fun error(s: String) {
        logger.error { s }
    }

    override fun info(s: String) {
        logger.info { s }
    }

}