var exec = require('cordova/exec');

exports.openDevice = function(success, error) {
    exec(success, error, "IdCard", "open", []);
};

exports.closeDevice = function(success, error) {
    exec(success, error, "IdCard", "read", []);
};

exports.closeDevice = function(success, error) {
    exec(success, error, "IdCard", "close", []);
};