# bettermockserver
A server for mocking http responses that supports fuzzy matching via regexes.

## Building ##

Build a jar with: ``` ./gradlew jar ```

Copy the resulting jar file to any working directory:

``` cp appcode/build/libs/appcode-1.0-SNAPSHOT.jar /myproject/server.jar ```

## Running ##
Start up the server with: ``` java -jar server.jar ```

List all mockpacks: ```java -jar server.jar -list```

Load specific mockpack: ```java -jar server.jar -mockpack [name]```

## Quick Start Example

First let's list the available mockpacks:

```
>start-server -list
Tiny Handy Mock Server
Version 1.0
----------------------
[main] INFO logging.ConsoleLogger - Available mockpacks:
[main] INFO logging.ConsoleLogger - "authenticated" - A mockpack representing an authenticated user that can access privileged resources.
[main] INFO logging.ConsoleLogger - "base" - Common base requests/responses for all mock packs.
[main] INFO logging.ConsoleLogger - "unauthenticated" - A mockpack representing an unauthenticated user.
[main] INFO logging.ConsoleLogger -
[main] INFO logging.ConsoleLogger - Run with: ./start-server -mockpack [packname]

```

Ok, Let's load up the authenticated mockpack:

```
>start-server -mockpack unauthenticated
Tiny Handy Mock Server
Version 1.0
----------------------
[main] INFO logging.ConsoleLogger - Loading the requested mockpack: authenticated
[main] INFO logging.ConsoleLogger - Processing file: mockpacks\base\config\stub\get.json
[main] INFO logging.ConsoleLogger - Processing file: mockpacks\authenticated\getprofile\stub\get.json
[main] INFO logging.ConsoleLogger - Loaded mockpack successfully
[main] INFO logging.ConsoleLogger - Mock server started on port 5050

```

Now we can hit the web server and in the logs we can also see how the matching result was obtained:

```$xslt
>curl -X POST http://localhost:5050/getprofile
{
  "name":"Joe User",
  "age": 33
}

...

Server logs:
 - Mock server: POST - /api/getprofile
 - mockpacks\authenticated\getprofile\stub\get.json is matched with points:4
 - mockpacks\authenticated\getprofile\stub\get.json is winning response
 - Matching mock has a simulated delay. Delaying for 3000 ms
```



## Mock Packs ##

Packs of mocks allow you to organise a series mocks by state or context.

For example, you might be modelling a rest API that has an authenticated and unauthenticated state:

```
| mockpacks\
|  base\...
|  authenticated\
|    get_profile\
|    delete_profile\
|  unauthenticated\
|    login_success\
|    login_fail\
|    get_profile_fail\
```

This folder structure allows us to implement login_success mocks only for the unauthenticated scenario, but it also allows us to specify a different response for the get_profile api call that should fail as we are unauthenticated.

A mockpack is made up of a mocksetup.txt file, and a set of folders. One folder per mock.

Mockpacks are hierarchical, and usually inherit from the 'Base' mockpack. This is configured in the mocksetup.txt file.

Each mock folder contains 3 subfolders: A Stub folder, a Body folder and a Header folder.

* The stub folder: Contains the api call definition, including URI, Method, Headers and Body.
* The body folder: If the stub references external body file for it's content, it will appear here.
* The header folder: If the stub references external header file for it's headers, it will appear here.

## The stub json file:

Each mock folder stored in a mockpack represents a single API call. The stub json file describes which api call will match with this stub. It does this by performing heuristic matching against uri, headers, body, method and it can do that with static matches or regex matching.


In the example below, this mock will match POST requests against the /api/authenticate api, with any value passed as the user agent header, and a user-token header that begins with 'api_'. The header logic matching is done with a regex:

```$xslt
{
  "request": {
    "headers": {
      "user-agent":"$(/.*/)"
      "user-token":"$(/api_.*/)"
    },
    "host": "http://127.0.0.1",
    "method": "POST",
    "uri": "/api/authenticate"
  },
  "response": {
    "headers": {
      "Date": "Tue, 06 Sep 2016 04:59:37 GMT",
      "AuthToken":"AT000z"
    },
    "body": "Authentication Successful",
    "code": 200,
    "message": "OK",
    "responseDelay": 3000
  }
}
```

## Regex matching ##

Regex matching is available for header values and body on request fields.

Example: Match all values: ``` $(/.*/) ```

Example: Match all values starting with Hello: ```$(/^Hello.*$/)```

Example: Match all values that have the word hello in the middle: ```$(/.*Hello.*/)```