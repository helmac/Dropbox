<!---
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
-->

# com.telerik.dropbox

The plugin is based on Dropbox Sync API.The Sync API takes care of syncing all your data with Dropbox through a familiar, file system-like interface. It's like giving your app its own, private Dropbox client.

The plugin defines a global `dropbox` object, which defines operations to setup and save files to your Dropbox folder.

Although the object is in the global scope, it is not available until after the `deviceready` event.

    document.addEventListener("deviceready", onDeviceReady, false);
    function onDeviceReady() {
        console.log(device.cordova);
    }

## Installation

You need to obtain dropbox APP_KEY and APP_SECRET. This will be prompted as you try to install the plugin. You can obtain these keys from the Dropbox [developer portal](https://www.dropbox.com/developers) under _App Console_:

Once you have your keys, you can install the plugin as follows:

    cordova plugin add url -—variable APP_KEY=# ---variable APP_SECRET=#

## Methods

- dropbox.linkAccount
- dropbox.linkedAccounts
- dropbox.save


## dropbox.linkAccount

Starts the authentication process to link a account.If your app is already linked, you can call this method again to prompt the user to link a different account, allowing your app to use multiple accounts at once (e.g. a personal and a business account).

	dropbox.linkAccount();
	
## dropbox.linkedAccounts

Returns all currently linked Dropbox accounts. If no accounts have been linked, this method will return an empty list.

The accounts are ordered from the least recently to the most recently linked.'

	dropbox.linkedAccounts(function(accounts){
		alert(JSON.stringify(accounts));
	});


## dropbox.save

Saves the given file(s) to dropbox for specific folder path otherwise in the root.

	var content = {
	    files : [path],
	    folder: "Saves"
	};
	window.dropbox.save(
	    content,
	    function (msg) {alert("SUCCESS: " + JSON.stringify(msg))},
	    function (msg) {alert("ERROR: "   + JSON.stringify(msg))}
	);

## Supported Platforms

- iOS
- Android

## Resources

For more information, please refer to the [Sync API documentation](https://www.dropbox.com/developers/sync) at developer portal.
