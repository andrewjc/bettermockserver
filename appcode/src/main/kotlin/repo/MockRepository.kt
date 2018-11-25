package repo

import arch.ServerApplication
import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import config.ServerAppConfig
import logging.ConsoleLogger
import logging.Logger

import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class MockRepository(private val appContext: ServerApplication, private val applicationConfiguration: ServerAppConfig, private val logger: Logger) {

    private val requestCollection: ConcurrentLinkedQueue<MockRepositoryEntry> = ConcurrentLinkedQueue()
    private val mockResponseQueue: ConcurrentLinkedQueue<MockRepositoryEntry> = ConcurrentLinkedQueue()
    private val MOCK_IDENTIFIER_SEPARATOR = "@"
    private var mockPackage: String? = null

    private val k: Klaxon = Klaxon()
    private val mapJsonConverter = object: Converter {
        override fun fromJson(jv: JsonValue): MutableMap<String, String> {
            val map = mutableMapOf<String, String>()

            val test = jv.obj!!.map
            test.forEach { it ->
                map[it.key] = it.value.toString()
            }

            return map
        }

        override fun canConvert(cls: Class<*>): Boolean {
            val can = cls == java.util.Map::class.java
            return can
        }

        override fun toJson(value: Any): String
        {
            return ""
        }

    }

    val allMockPacks: List<MockConfigData>
        get() {
            if( File(MOCKPACK_FOLDER_NAME).isDirectory == false) {
                logger.info("Mockpack directory not found. Please create a mockpack directory.")
                return emptyList()
            }
            val mockPacks = FileUtils().listFiles(MOCKPACK_FOLDER_NAME)
            val mockList = ArrayList<MockConfigData>()
            for (mockPackName in mockPacks) {
                if (FileUtils().fileExists(MOCKPACK_FOLDER_NAME + File.separator + mockPackName + File.separator + MOCKSETUP_FILE_NAME)) {
                    var configData: MockConfigData? = null
                    try {

                        configData = k.parse<MockConfigData>(FileUtils().readAllText(MOCKPACK_FOLDER_NAME + File.separator + mockPackName + File.separator + MOCKSETUP_FILE_NAME))

                        configData!!.path = mockPackName
                        mockList.add(configData)
                    } catch (e: Exception) {
                        logger.info("Unable to read mock file: " + e.message)
                        continue
                    }

                }
            }

            return mockList
        }

    fun setMockPack(mockPackName: String): Boolean {
        logger.info("Loading the requested mockpack: $mockPackName")

        var ret = false
        if (this.mockPackage != null && this.mockPackage!! == mockPackName) {

            var configData: MockConfigData? = null
            try {
                configData = Klaxon().parse<MockConfigData>(FileUtils().readAllText(MOCKPACK_FOLDER_NAME + File.separator + mockPackName + File.separator + MOCKSETUP_FILE_NAME))
            } catch (e: Exception) {
                logger.debug("Unable to read mock file: $e")
                return false
            }

            mockResponseQueue!!.clear()
        } else {

            this.mockPackage = mockPackName

            this.requestCollection.clear()
            this.mockResponseQueue!!.clear()

            val mockEntryMap = mutableMapOf<String, MockRepositoryEntry>()
            ret = processMockPack(mockPackName, mockEntryMap)

            val keyset = mockEntryMap.keys.iterator()

            while (keyset.hasNext()) {
                requestCollection?.add(mockEntryMap[keyset.next()])
            }

            if (ret)
                logger.info("Loaded mockpack successfully")

        }
        return ret
    }

    private fun checkMockEntryBody(entry: MockRepositoryEntry?, apiFolderRoot: String) {
        if (entry == null)
            return

        //populate request header
        try {
            populateHeader(entry.request.headers, apiFolderRoot)
        } catch (e: IOException) {
            logger.error("Error loading mock file: " + entry.filename + ". Can not populate request header")
        }

        //populate response header
        try {
            populateHeader(entry.response.headers, apiFolderRoot)
        } catch (e: IOException) {
            logger.error("Error loading mock file: " + entry.filename + ". Can not populate response header")
        }

        //populate response body
        try {
            populateBody(entry.response, apiFolderRoot)
        } catch (e: IOException) {
            logger.error("Error loading mock file: " + entry.filename + ". Can not populate body file: " + entry.response.body)
        }

    }

    @Throws(IOException::class)
    private fun populateHeader(map: MutableMap<String, String>, apiFolderRoot: String) {

        for (headerEntry in map.entries) {
            if (headerEntry.key == "file") {
                val headerFile = shouldReplaceContent(headerEntry.value)
                if (headerFile != null) {
                    //header file is taken out, so remove it
                    map.remove("file")

                    val headerFilePath: String

                    if (headerFile.contains(File.separator)) {
                        //the header file is located in parent mock pack folder
                        val headerFileParts = headerFile.split("/")
                        if (headerFileParts.size == 2) {
                            headerFilePath = MOCKPACK_FOLDER_NAME + File.separator +
                                    headerFileParts[0] + File.separator +
                                    File(apiFolderRoot).name + File.separator +
                                    HEADER_FOLDER + File.separator + headerFileParts[1]
                        } else {
                            //wrong format, exit parsing
                            return
                        }
                    } else {
                        //the header file is located in current mock pack folder
                        headerFilePath = apiFolderRoot  + HEADER_FOLDER + File.separator + headerFile
                    }

                    val m : MutableMap<String, String> = k.converter(mapJsonConverter).parse(FileUtils().readAllText(headerFilePath))!!

                    m.forEach { t, u -> map[t.toLowerCase()] = u }
                }

                return
            }
        }
    }

    @Throws(IOException::class)
    private fun populateBody(response: Response, apiFolderRoot: String) {
        val responseBodyFile = shouldReplaceContent(response.body)
        if (responseBodyFile != null) {

            val bodyFilePath: String
            if (responseBodyFile.contains(File.separator)) {
                //the header file is located in parent mock pack folder
                val bodyFileParts = responseBodyFile.split(File.separator)
                if (bodyFileParts.size == 2) {
                    bodyFilePath = MOCKPACK_FOLDER_NAME + File.separator +
                            bodyFileParts[0] + File.separator +
                            File(apiFolderRoot).name + File.separator +
                            BODY_FOLDER + File.separator + bodyFileParts[1]
                } else {
                    //wrong format, exit parsing
                    return
                }
            } else {
                //the header file is located in current mock pack folder
                bodyFilePath = apiFolderRoot + File.separator + BODY_FOLDER + File.separator + responseBodyFile
            }

            response.body = FileUtils().readAllText(bodyFilePath)
        }
    }

    private fun shouldReplaceContent(value: String?): String? {
        if (value == null) return null

        val trimmedValue = value.trim()
        return if (trimmedValue.startsWith(START_EXP_TOKEN + FILE_EXP_TOKEN) && trimmedValue.endsWith(END_EXP_TOKEN)) {
            trimmedValue.substring((START_EXP_TOKEN + FILE_EXP_TOKEN).length, trimmedValue.length - END_EXP_TOKEN.length)
        } else null

    }

    private fun processMockPack(mockPackName: String?, mockEntryMap: MutableMap<String, MockRepositoryEntry>): Boolean {
        var loadedSuccessfully = true

        if (mockPackName.isNullOrEmpty()) return false

        val configData: MockConfigData?
        try {
            configData = Klaxon().parse<MockConfigData>(FileUtils().readAllText(MOCKPACK_FOLDER_NAME + File.separator + mockPackName + File.separator + MOCKSETUP_FILE_NAME))
        } catch (e: Exception) {
            logger.info("Failed reading mock file: " + e.message)
            return false
        }

        if (configData != null) {
            if (configData!!.parent != null && !configData!!.parent!!.trim().isEmpty()) {
                logger.debug("Processing parent: " + configData!!.parent!!)
                loadedSuccessfully = processMockPack(configData!!.parent, mockEntryMap)
            }

            logger.debug("Processing mockpack: " + mockPackName!!)

            // Load all files from the prepackaged assets
            val bundledFiles = FileUtils().listFiles(MOCKPACK_FOLDER_NAME + File.separator + mockPackName)
            if (bundledFiles != null && bundledFiles.isNotEmpty()) {
                for (file in bundledFiles!!) {
                    val currentEntryPath = MOCKPACK_FOLDER_NAME + File.separator + mockPackName + File.separator + file

                    try {

                        if (FileUtils().isFolder(currentEntryPath)) {
                            if (file.contains(MOCKSETUP_FILE_NAME) || file.contains("."))
                                continue

                            if (FileUtils().isFolder(currentEntryPath + File.separator + STUB_FOLDER)) {
                                // Process all in stub folder
                                processStubFolder(currentEntryPath + File.separator + STUB_FOLDER, mockEntryMap)
                            } else {
                                // Process all files in this folder.
                                val stubs = FileUtils().listFiles(currentEntryPath)
                                if (stubs.isNotEmpty()) {
                                    for (stub in stubs)
                                        processStub(currentEntryPath + File.separator + stub, mockEntryMap)
                                }
                            }

                        } else {
                            // Process as a V1 flat mock entry

                            if (file.contains(MOCKSETUP_FILE_NAME) || file.endsWith(".body"))
                                continue

                            processStub(currentEntryPath, mockEntryMap)
                        }

                    } catch (e: IOException) {
                        loadedSuccessfully = false

                        logger.debug("Unable to parse bundled mock file: " + file + ", reason: " + e.message)
                    }

                }
            }
        }

        if (!loadedSuccessfully)
            logger.error("Warning: Unable to load the requested mockpack!")

        return loadedSuccessfully
    }

    @Throws(IOException::class)
    private fun processStubFolder(stubFolderPath: String, mockEntryMap: MutableMap<String, MockRepositoryEntry>) {
        val stubs = FileUtils().listFiles(stubFolderPath)

        for (stub in stubs) {
            processStub(stubFolderPath + File.separator + stub, mockEntryMap)
        }
    }

    @Throws(IOException::class)
    private fun processStub(file: String, mockEntryMap: MutableMap<String, MockRepositoryEntry>) {
        val json = FileUtils().readAllText(file)
        val cacheEntry = Klaxon().parse<MockRepositoryEntry>(json)

        if(cacheEntry != null) {
            logger.info("Processing file: $file")
            cacheEntry.filename = file
            cacheEntry.id = file + "@" + file.substring(file.lastIndexOf(File.separator))

            val apiFolderRoot = getApiRootDirectory(file)

            //Replace file name with its actual content
            checkMockEntryBody(cacheEntry, apiFolderRoot)

            if(cacheEntry.id == null) throw IOException("Invalid entry id.")

            mockEntryMap[cacheEntry.id!!] = cacheEntry
        }

    }

    private fun getApiRootDirectory(file: String): String {
        val bits = file.split(File.separator)

        if(bits[bits.size - 2] == "stub") {
            return bits.joinToString(limit = bits.size -2, separator = File.separator, postfix = "", prefix = "", truncated = "")
        }
        else return bits.joinToString(separator = File.separator, postfix = "", prefix = "", truncated = "")

    }

    @Throws(Exception::class)
    operator fun get(entry: MockRepositoryEntry): MockRepositoryEntry? {
        // If the request queue has anything in it, return from that.
        logger.debug("Attempting to find match for:")
        logger.debug("URI: " + entry.request.uri!!)
        logger.debug("Method: " + entry.request.method!!)

        if (!mockResponseQueue!!.isEmpty()) {
            // If the item at the top of the queue matches the request we're looking for
            // return that
            val iter = mockResponseQueue.iterator()
            while (iter.hasNext()) {
                val topItem = iter.next()

                val methodMatches: Boolean = topItem.request.method!!.contentEquals(entry.request.method!!)
                val hostMatches: Boolean = topItem.request.host!!.contentEquals(entry.request.host!!)

                if (hostMatches && methodMatches) {
                    if (topItem.request.uri!!.startsWith(START_EXP_TOKEN)) {
                        val expression = topItem.request.uri!!.substring("$(/".length, topItem.request.uri!!.length - "/)".length)
                        if (!entry.request.uri!!.matches(Regex(expression))) continue
                    } else {
                        if (!topItem.request.uri!!.contentEquals(entry.request.uri!!)) continue
                    }

                    mockResponseQueue.remove(topItem)
                    logger.debug("Queued response " + topItem!!.id + " is a match for " + entry.request.uri)

                    return topItem
                }
            }
        }

        // Find a best fit in our repository for the given request

        val orderedMap = sortedMapOf<Int, MockRepositoryEntry>()

        entry.request.body!!.keys.stream().filter { key -> entry.request.body!!.containsKey(key) }.forEach { key ->
            val incomingBody = getBodyContent(entry.request.body!![key])

            if (incomingBody.isNullOrEmpty())
                logger.debug("Body: None")
            else {
                logger.debug("Body Key: $key")
                logger.debug("Body Content: " + incomingBody!!)
            }
        }

        if (requestCollection != null) {
            for (entryIncache in requestCollection) {
                if (entryIncache.request.host == null || entryIncache.request.uri == null || entryIncache.request.method == null)
                    continue


                // Number of matches to this cache entry
                var matchingElements = 0

                // MUST MATCH THESE
                if (!entryIncache.request.host!!.contentEquals(entry.request.host!!)) continue

                if (entryIncache.request.uri!!.startsWith(START_EXP_TOKEN)) {
                    val expression = entryIncache.request.uri!!.substring("$(/".length, entryIncache.request.uri!!.length - "/)".length)
                    if (!entry.request.uri!!.matches(Regex(expression))) continue
                } else {
                    if (!entryIncache.request.uri!!.contentEquals(entry.request.uri!!)) continue
                }

                if (!entryIncache.request.method!!.contentEquals(entry.request.method!!)) continue

                matchingElements = 3 //we know that host, uri and method matched

                // Should match as many of these as possible.
                for (key in entryIncache.request.headers.keys) {

                    if (entry.request.headers.containsKey(key)) {
                        val incomingHeaderValue = entry.request.headers[key]
                        val templateHeaderValue = entryIncache.request.headers[key]
                        // If the value begins with the regular expression START_TOKEN
                        // then we check it as a regular expression
                        if (templateHeaderValue!!.startsWith(START_EXP_TOKEN)) {
                            val expression = templateHeaderValue.substring("$(/".length, templateHeaderValue.length - "/)".length)
                            val matches = incomingHeaderValue!!.matches(Regex(expression))
                            matchingElements += if (matches) 1 else 0
                        } else {
                            matchingElements += if (entry.request.headers.containsKey(key) && entryIncache.request.headers[key]!!.contentEquals(entry.request.headers[key]!!)) 1 else 0
                        }
                    }
                }


                if (entryIncache.request.queryString != null && !entryIncache.request.queryString!!.isEmpty()
                        && entry.request.queryString != null && !entry.request.queryString!!.isEmpty()) {

                    val incomingQueryString = entry.request.queryString
                    val cacheQueryString = entryIncache.request.queryString
                    var tmp = false
                    if (cacheQueryString!!.startsWith(START_EXP_TOKEN)) {
                        val expression = cacheQueryString.substring("$(/".length, cacheQueryString.length - "/)".length)
                        tmp = incomingQueryString!!.matches(Regex(expression))
                        if (tmp)
                            matchingElements += 10000000
                        else
                            continue
                    }
                    if (!tmp) {
                        val similarity = Math.max(incomingQueryString!!.length, incomingQueryString.length) - levenshteinDistance(incomingQueryString, cacheQueryString)
                        matchingElements += similarity
                    }

                }

                // Needs to match as much of the body request

                var bodyMatch = true
                if(entryIncache.request.body == null && entry.request.body == null) {
                    matchingElements += 1
                }
                else if (entryIncache.request.body != null && entry.request.body != null) {
                    for (key in entryIncache.request.body!!.keys) {
                        if (entry.request.body!!.containsKey(key)) {
                            val incomingBody = getBodyContent(entry.request.body!![key])!!.trim()
                            val cacheBody = entryIncache.request.body!![key]!!.trim()

                            var tmp = false
                            if (cacheBody.startsWith(START_EXP_TOKEN)) {
                                val expression = cacheBody.substring("$(/".length, cacheBody.length - "/)".length)
                                tmp = incomingBody.matches(Regex(expression))
                                if (tmp) {
                                    matchingElements += 10000000
                                    bodyMatch = true
                                } else {
                                    bodyMatch = false
                                    continue
                                }
                            }
                            if (!tmp) {
                                val similarity = Math.max(incomingBody.length, cacheBody.length) - levenshteinDistance(incomingBody, cacheBody)
                                matchingElements += similarity
                                bodyMatch = true
                            }
                        } else {
                            bodyMatch = false
                        }
                    }
                }
                if (!bodyMatch) continue

                matchingElements += entryIncache.priority!!

                logger.info(entryIncache.filename + " is matched with points:" + matchingElements)

                // If two scores are equal, prefer something without a sequence ID
                //if(entryIncache.id != null && orderedMap.keys.contains(matchingElements))
                orderedMap[matchingElements] = entryIncache

            }
        }

        if (orderedMap.isEmpty()) return null

        val matched = orderedMap.entries.last().value
        if (matched != null) {
            // Sanity check that the incoming body key is equal to the matched body key
            if (matched.request.body != null && !matched.request.body!!.isEmpty()) {
                val matchedBodyKey = matched.request.body!!.firstKey()
                val incomingBodyKey = entry.request.body!!.firstKey()
                if (!matchedBodyKey.contentEquals(incomingBodyKey)) {
                    logger.debug("Warning: Incoming body key ($incomingBodyKey) is different from matched body key: $matchedBodyKey")
                }
            }

            if (!matched.dontAssertHeaders) {
                for (key in matched.request.headers.keys) {
                    val templateHeader = matched.request.headers[key]

                    if (!entry.request.headers.containsKey(key)) {
                        logger.debug("Warning: Incoming request was missing a header present in template: $key")
                        throw Exception("Incoming request was missing a header present in template: $key")
                    } else {
                        val incomingHeader = entry.request.headers[key]!!
                        if (templateHeader!!.startsWith(START_EXP_TOKEN)) {
                            val expression = templateHeader.substring("$(/".length, templateHeader.length - "/)".length)
                            val headerMatches = incomingHeader.matches(Regex(expression))
                            if (!headerMatches) {
                                logger.debug("Warning: Incoming request header ($key) did not match expression: $expression")
                                logger.debug("Warning: The incoming value was  :$incomingHeader")
                                logger.debug("Warning: Template filename       : " + matched.filename!!)
                                logger.debug("Warning: Request URI             : " + entry.request.uri!!)
                                logger.debug("Warning: Request Query           : " + entry.request.queryString!!)
                                throw Exception("Incoming request header $key did not match expression: $expression")
                            }
                        } else {
                            if (!incomingHeader.contentEquals(templateHeader)) {
                                logger.debug("Warning: Incoming request header ($key) did not match expression: $templateHeader")
                                logger.debug("Warning: The incoming value was  :$incomingHeader")
                                logger.debug("Warning: Template filename       : " + matched.filename!!)
                                logger.debug("Warning: Request URI             : " + entry.request.uri!!)
                                logger.debug("Warning: Request Query           : " + entry.request.queryString!!)
                                throw Exception("Incoming request header $key did not match expression: $templateHeader")
                            }
                        }
                    }
                }
            }
        }


        if (matched == null) {
            logger.debug("No match found")
            return null
        }

        logger.info(matched!!.filename!! + " is winning response")

        return matched
    }

    private fun levenshteinDistance(lhs: String, rhs: String): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = Array(lhsLength) { it }
        var newCost = Array(lhsLength) { 0 }

        for (i in 1 until rhsLength) {
            newCost[0] = i

            for (j in 1 until lhsLength) {
                val match = if(lhs[j - 1] == rhs[i - 1]) 0 else 1

                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1

                newCost[j] = Math.min(Math.min(costInsert, costDelete), costReplace)
            }

            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[lhsLength - 1]
    }

    /*
        The internal web server
     */
    private fun getBodyContent(rawBody: String?): String? {
        if (!rawBody.isNullOrBlank()) {
            if (rawBody!!.contains(TEMP_DIRECTORY) && rawBody.contains("/cache/")) {
                // Read contents from the temp file.
                return try {
                    FileUtils().readAllText(rawBody)
                } catch (e: IOException) {
                    rawBody
                }

            }

            return rawBody
        } else
            return null
    }

    fun clearAllPushResponses() {
        mockResponseQueue?.clear()
    }

    fun pushResponse(entry: MockRepositoryEntry) {
        mockResponseQueue!!.add(entry)
    }

    fun pushResponse(mockSequenceId: String) {
        val entry = findRequest(mockSequenceId)
        if (entry != null)
            mockResponseQueue!!.add(entry)
        else
            throw RuntimeException("No response to push to mock queue could be found: $mockSequenceId")
    }

    private fun findRequest(mockSequenceId: String): MockRepositoryEntry? {
        if (requestCollection != null) {
            for (e in requestCollection) if (e.id != null && e.id!!.contentEquals(mockSequenceId)) {
                logger.debug("Found mock response with sequence ID $mockSequenceId")
                return e
            }
        }
        return null
    }

    companion object {

        val MOCKPACK_FOLDER_NAME = "mockpacks"
        val MOCKSETUP_FILE_NAME = "mocksetup.txt"
        val TEMP_DIRECTORY = System.getProperty("java.io.tmpdir")
        private val START_EXP_TOKEN = "$(/"
        private val END_EXP_TOKEN = "/)"
        private val FILE_EXP_TOKEN = "file:"

        private val BODY_FOLDER = "body"
        private val HEADER_FOLDER = "header"
        private val STUB_FOLDER = "stub"
    }
}
