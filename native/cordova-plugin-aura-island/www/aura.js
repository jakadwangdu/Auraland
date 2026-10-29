var exec = require('cordova/exec');
function call(action, args) {
  return new Promise(function (resolve, reject) { exec(resolve, reject, 'AuraIsland', action, args || []); });
}
module.exports = {
  status: function () { return call('status'); },
  requestOverlay: function () { return call('requestOverlay'); },
  openNotificationAccess: function () { return call('openNotificationAccess'); },
  openBatterySettings: function () { return call('openBatterySettings'); },
  start: function () { return call('start'); },
  stop: function () { return call('stop'); },
  setHidePreview: function (v) { return call('setHidePreview', [!!v]); },
  test: function () { return call('test'); }
};
