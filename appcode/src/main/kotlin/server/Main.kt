package server

import arch.ServerApplication
import config.ServerAppConfig
import logging.ConsoleLogger
import logging.Logger
import repo.MockRepository

private var app: ServerApplication? = null
private var appConfig: ServerAppConfig? = null
private var logger: Logger? = null

fun main(args: Array<String>?) {
    appConfig = ServerAppConfig(false, "", 5050)
    logger = ConsoleLogger()
    app = ServerApplication(appConfig!!, logger as ConsoleLogger)

    var mockPackName = "base"
    var debugMode = false

    System.out.println("Tiny Handy Mock Server")
    System.out.println("Version 1.0")
    System.out.println("----------------------")

    var tokenLoopArg = ""
    if (args != null) {
        for (str in args) {
            if (str.toLowerCase() == "-mockpack" ||
                    str.toLowerCase() == "-pack" ||
                    str.toLowerCase() == "-packname" ||
                    str.toLowerCase() == "-name") {
                mockPackName = "?"
                tokenLoopArg = "mockpack"
            } else if (str.toLowerCase() == "-list") {
                logger = ConsoleLogger()
                showAllMockPacks(app!!)
                return
            } else if (str.toLowerCase() == "-port") {
                tokenLoopArg = "port"
            } else if (str.toLowerCase() == "-help") {
                showHelp()
                return
            } else if (str.toLowerCase() == "-debug") {
                appConfig!!.debugMode = true
            } else if (tokenLoopArg == "mockpack") {
                appConfig!!.mockPackName = str
                mockPackName = str
            } else if(tokenLoopArg == "port") {
                appConfig!!.port = str.toInt()
            }
        }
    }


    if (mockPackName == "?" || mockPackName == "") {
        logger!!.info("Usage: ./start-server -mockpack [mockpackname]")
        logger!!.info("Tip: -help to show usage")
        return
    }

    app!!.onStart()
}

private fun showAllMockPacks(app: ServerApplication) {
    logger!!.info("Available mockpacks:")
    val mockRepository = MockRepository(app!!, app.appConfig!!, app.logger as ConsoleLogger)
    val mocks = mockRepository.allMockPacks
    mocks.stream().filter { d -> !d.isHidden }.forEachOrdered { d -> logger!!.info("\"" + d.path + "\"" + " - " + d.description) }
    logger!!.info("")
    logger!!.info("Run with: ./start-server -mockpack [packname]")
}

private fun showHelp() {
    logger!!.info("Usage: ./start-server -mockpack [mockpackname]")
    logger!!.info("                      -list (List all mockpacks)")
    logger!!.info("                      -debug (Show verbose info)")
    logger!!.info("                      -socket-timeout [timeout-in-sec]")
    logger!!.info("                      -help (Show this info)")
}