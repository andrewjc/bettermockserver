package repo

import com.beust.klaxon.Json
import java.util.*

data class MockRepositoryEntry(

        var request: Request = Request(),

        var response: Response = Response(),

        var dontAssertHeaders: Boolean = false,

        var runMode: String? = null,

        var id: String? = null,

        var priority: Int? = 0,

        var filename: String? = ""

)

data class Request(var method: String? = null,
                   var uri: String? = null,

                   var queryString: String? = null,

                   @Json(name = "headers")
                   var headers: MutableMap<String,String> = mutableMapOf(),

                   var host: String? = null,

                   var body: TreeMap<String, String>? = null,

                   var filters: ArrayList<MatchFilter>? = null)

data class Response(
        @Json(name = "headers")
        var headers: MutableMap<String,String> = mutableMapOf(),

        var body: String? = null,
        var code: Int = 0,
        var message: String? = null,
        var responseDelay: Int? = null)


data class MatchFilter(
    var propertyName: String? = null,
    var expression: String? = null
)


data class MockConfigData(

    @Json("Name")
    var name: String? = null,

    @Json("Description")
    var description: String? = null,

    @Json("Parent")
    var parent: String? = null,

    @Json("BasicAuth")
    var basicAuth: BasicAuthCredentials? = null,

    @Json("IsHidden")
    var isHidden: Boolean = false,

    @Json("Sequence")
    var sequenceList: List<String>? = null,

    @Json("Index")
    var index: Int = 0,

    @Json(ignored = true)
    var external: Boolean = false,
    @Json(ignored = true)
    var path: String? = null

)

data class BasicAuthCredentials (
        var username: String? = null,
        var password: String? = null
)
