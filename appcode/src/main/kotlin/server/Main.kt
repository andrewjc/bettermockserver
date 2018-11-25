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
                mockPackName = str
            } else if(tokenLoopArg == "port") {
                appConfig!!.port = str.toInt()
            }
        }
    }


    if (mockPackName == "?" || mockPackName == "") {
        System.out.println("Usage: ./start-server -mockpack [mockpackname]")
        System.out.println("Tip: -help to show usage")
        return
    }

    app!!.onStart()
}

private fun showAllMockPacks(app: ServerApplication) {
    System.out.println("Available mockpacks:")
    val mockRepository = MockRepository(app!!, app.appConfig!!, app.logger as ConsoleLogger)
    val mocks = mockRepository.allMockPacks
    mocks.stream().filter { d -> !d.isHidden }.forEachOrdered { d -> System.out.println("\"" + d.path + "\"" + " - " + d.description) }
    System.out.println("")
    System.out.println("Run with: ./start-server -mockpack [packname]")
}

private fun showHelp() {
    System.out.println("Usage: ./start-server -mockpack [mockpackname]")
    System.out.println("                      -list (List all mockpacks)")
    System.out.println("                      -debug (Show verbose info)")
    System.out.println("                      -socket-timeout [timeout-in-sec]")
    System.out.println("                      -help (Show this info)")
}