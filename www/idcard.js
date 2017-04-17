var exec = require('cordova/exec');

exports.open = function(success, error) {
    exec(success, error, "IdCard", "open", []);
};

exports.read = function(success, error) {
    exec(success, error, "IdCard", "read", []);
};

exports.close = function(success, error) {
    exec(success, error, "IdCard", "close", []);
};