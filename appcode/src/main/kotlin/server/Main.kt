package server

import arch.ServerApplication
import com.beust.klaxon.Klaxon
import config.ServerAppConfig
import logging.ConsoleLogger
import logging.Logger
import repo.MockConfigData
import repo.MockRepository
import repo.MockRepositoryEntry
import java.io.File

private var app: ServerApplication? = null
private var appConfig: ServerAppConfig? = null
private var logger: Logger? = null

fun main(args: Array<String>?) {

    var mockPackName = "base"
    var debugMode = false
    var serverPort = 5050

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
                logger = ConsoleLogger(debugMode)
                showAllMockPacks()
                return
            } else if (str.toLowerCase() == "-port") {
                tokenLoopArg = "port"
            } else if (str.toLowerCase() == "-help") {
                showHelp()
                return
            } else if (str.toLowerCase() == "-debug") {
                debugMode = true
            } else if (tokenLoopArg == "mockpack") {
                mockPackName = str
            } else if(tokenLoopArg == "port") {
                serverPort = str.toInt()
            }
        }
    }

    appConfig = ServerAppConfig(debugMode, mockPackName, serverPort)
    logger = ConsoleLogger(appConfig!!.debugMode)

    if (mockPackName == "?" || mockPackName == "") {
        System.out.println("Usage: ./start-server -mockpack [mockpackname]")
        System.out.println("Tip: -help to show usage")
        return
    }

    app = ServerApplication(appConfig!!, logger as ConsoleLogger)

    app!!.onStart()
}

private fun showAllMockPacks() {
    System.out.println("Available mockpacks:")
    val mockRepository = MockRepository(app!!, appConfig!!, logger as ConsoleLogger)
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