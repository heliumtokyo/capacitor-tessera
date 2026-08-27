import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const platform = process.argv[2];

if (platform !== 'android' && platform !== 'ios') {
  throw new Error('Expected one platform argument: android or ios.');
}

const npx = process.platform === 'win32' ? 'npx.cmd' : 'npx';

if (!existsSync(platform)) {
  run('cap', 'add', platform);
}

if (platform === 'android') {
  replaceInFile('android/variables.gradle', /compileSdkVersion\s*=\s*\d+/, 'compileSdkVersion = 37');
  replaceInFile('android/variables.gradle', /targetSdkVersion\s*=\s*\d+/, 'targetSdkVersion = 37');
} else {
  replaceInFile(
    'ios/App/App.xcodeproj/project.pbxproj',
    /IPHONEOS_DEPLOYMENT_TARGET = [\d.]+;/g,
    'IPHONEOS_DEPLOYMENT_TARGET = 18.0;',
  );
  replaceInFile('ios/App/CapApp-SPM/Package.swift', /\.iOS\(\.v\d+\)/, '.iOS(.v18)');

  const infoPlistPath = 'ios/App/App/Info.plist';
  const infoPlist = readFileSync(infoPlistPath, 'utf8');
  if (!infoPlist.includes('<key>NSCameraUsageDescription</key>')) {
    if (!infoPlist.includes('</dict>')) {
      throw new Error(`Expected closing dictionary was not found in ${infoPlistPath}.`);
    }
    writeFileSync(
      infoPlistPath,
      infoPlist.replace(
        '</dict>',
        '\t<key>NSCameraUsageDescription</key>\n\t<string>Scan the machine-readable zone on an identity document.</string>\n</dict>',
      ),
    );
  }
}

run('cap', 'sync', platform);

function replaceInFile(path, pattern, replacement) {
  const original = readFileSync(path, 'utf8');
  if (!pattern.test(original)) {
    throw new Error(`Expected pattern was not found in ${path}.`);
  }
  pattern.lastIndex = 0;
  const updated = original.replace(pattern, replacement);
  if (updated !== original) {
    writeFileSync(path, updated);
  }
}

function run(...args) {
  const result = spawnSync(npx, args, { stdio: 'inherit' });
  if (result.status !== 0) {
    throw new Error(`npx ${args.join(' ')} failed with status ${result.status}.`);
  }
}
