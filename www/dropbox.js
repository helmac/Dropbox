var exec = require('cordova/exec');
var channel = require('cordova/channel');

function Dropbox(){
}

Dropbox.prototype.linkAccount = function(successCallback, errorCallback){
  exec(successCallback, errorCallback, "Dropbox", "linkAccount", []);
};

Dropbox.prototype.linkedAccounts = function(successCallback, errorCallback){
    exec(successCallback, errorCallback, "Dropbox", "linkedAccounts", []);
};

Dropbox.prototype.save = function(arg, successCallback, errorCallback){
		exec(successCallback, errorCallback, "Dropbox", "saveFile", [arg]);
};

Dropbox.prototype.onProgressUpload = function(successCallback, errorCallback){
		exec(successCallback, errorCallback, "Dropbox", "onProgressUpload", []);
};


if (typeof module != 'undefined' && module.exports) {
  module.exports = new Dropbox();
}

