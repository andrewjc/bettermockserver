# bettermockserver
A server for mocking http responses that supports fuzzy matching via regexes.

## Building ##

Build a jar with: ``` ./gradlew jar ```

Copy the resulting jar file to any working directory:

``` cp appcode/build/libs/appcode-1.0-SNAPSHOT.jar /myproject/server.jar ```

## Running ##
Start up the server with: ``` java -jar server.jar ```