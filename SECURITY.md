# Security Policy

Please contact us directly about security issues: [136268370+thies2005@users.noreply.github.com](mailto:136268370+thies2005@users.noreply.github.com). Please give appropriate time to respond.

This is a community project without 24/7 operations or formal security review. While we try to make the app as secure as possible, we cannot offer any guarantees. Do not rely on the encryption for your safety.

## Supported Versions

We only support the latest release version. Of course, we still like to hear about vulnerabilities in pre-release versions and older still-in-use versions.

## Security Model
Since the app is a file browser with the keys to your cloud storage as well as local storage, we consider anything a security issue that...
- allows unauthorized access to oauth tokens, passwords and other secrets,
- allows unintended read/write access to cloud content,
- allows access to private app storage, code or otherwise compromises the functionality.


The app relies on the platform for protecting its private directories and executables. We therefore explicitly do not support rooted devices or devices with unpatched vulnerabilities. If it is technically feasable to add mitigations against platform vulnerabilites, we will consider it.

## Current state of security
_While we do not consider those things security vulnerabilities, they are on our "security" todo list for new/improved protections._
- The rclone configuration file is stored without additional encryption in app-private storage ([issue](https://github.com/x0b/rcx/issues/12)).
- Some operations may use your flash storage as temporary storage location, if they are too large for app-internal storage. Depending on your Android version, those files may temporarily be available to other apps that you have granted external storage or storage manager permissions.
- The upcoming SAF provider offers other apps direct access to your files. It is vital you do not install apps you do not trust 100%, since they may be able to impersonate apps you have previously granted file access to.
