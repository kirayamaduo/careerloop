const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const fail = (message) => {
  throw new Error(`[wechat-privacy] ${message}`);
};
const requireText = (source, needle, message) => {
  if (!source.includes(needle)) fail(message);
};

const manifestText = read('src/manifest.json')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/^\s*\/\/.*$/gm, '');
const manifest = JSON.parse(manifestText);
const wechat = manifest['mp-weixin'];
if (!wechat || wechat.__usePrivacyCheck__ !== true) {
  fail('manifest mp-weixin.__usePrivacyCheck__ must be true');
}

const room = read('src/pages/interview/room.vue');
[
  ['wxApi.onNeedPrivacyAuthorization', 'missing onNeedPrivacyAuthorization registration'],
  ['offNeedPrivacyAuthorization', 'privacy listener is not removed when the room closes'],
  ['open-type="agreePrivacyAuthorization"', 'missing official privacy-agreement button'],
  ['@agreeprivacyauthorization="agreeWechatPrivacyAuthorization"', 'privacy button is not wired'],
  ["event: 'agree'", 'agree callback does not resolve the pending protected API'],
  ["event: 'disagree'", 'reject/unmount path does not resolve as disagree'],
  ['wxApi.openPrivacyContract', 'privacy contract cannot be opened'],
  ['requestCamera(false)', 'camera must start in non-interactive permission-check mode'],
  ["scope: 'scope.camera'", 'camera scope authorization is missing'],
  ["'scope.record'", 'recording scope recovery is missing'],
].forEach(([needle, message]) => requireText(room, needle, message));

const registerAt = room.indexOf('registerWechatPrivacyAuthorization();');
const recorderAt = room.indexOf('initRecorder();');
const cameraAt = room.indexOf('requestCamera(false);');
if (registerAt < 0 || recorderAt < 0 || cameraAt < 0
    || registerAt > recorderAt || registerAt > cameraAt) {
  fail('privacy handler must register before recorder/camera operations');
}

console.log('[wechat-privacy] manifest and interview privacy flow checks passed');
