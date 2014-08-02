WebPrefs
========

WebPrefs is an experimental API for storing mod preferences "in the cloud" but with a unique twist that
preferences can be either **public** or **private**. **Private** values work as you'd expect, with only
your client being able to read and write values in the store. **Public** values are readable by any other
minecraft client which means that they can be used to communicate your preferences for cosmetic mods
to other players.

As an example, consider a client-side-only cosmetic mod which allows you to set the scale of different
parts of your player model. By storing the preferences for part scales "in the cloud" as public properties,
anyone else with the mod installed will be able to see your selection.

The current implementation only stores and retrieves properties on a best-effort basis and may need some
improved error handling in the future. It is also prone to hitting the throttling limits if methods are
called incorrectly.

Once I am happy that the API is stable I will also release the server-side implementation of the key/value
store so that mods are free to provide their own property repositories and don't need to rely on mine.


Session handling in WebPrefs
----------------------------

Session validation is done by using the legacy mojang server connection API to both validate the connection
at the client before posting to the server. Session validation occurs thus:

 * The client makes a KEY request to the server to request an identifying key. This key can be arbitrarily generated by the server based on the calling client.
 * The client calls the legacy mojang API endpoint to indicate that it is "joining server with ID" and supplies its access token (acquired at login) and the generated key from the server.
 * The client makes a GET or SET query against the server.
 * For public GET requests, no furthe validation is performed.
 * For private GET requests and and SET request, the server then calls back against the mojang API to check if the client has "joined" the server with the ID it supplied to the client.

The authentication mechanism is thus the same as that used to connect to minecraft servers and validates the
user's session without sending the user's authentication token to the (untrusted) remote server.


Building WebPrefs
-----------------

Building WebPrefs is currently not recommended since it is still in development and may change. If you want
to "shade" it into your mods you may freely do so however.