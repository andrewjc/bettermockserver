package logging

open interface Logger {
    fun info(s: String)

    fun error(s: String)

    fun debug(s: String)
}
