# Remote Manager 📱☁️

[![GitHub release](https://img.shields.io/github/v/release/neubofy/Remote-Manager?include_prereleases&style=for-the-badge&color=blue)](https://github.com/neubofy/Remote-Manager/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/neubofy/Remote-Manager/release.yml?branch=master&style=for-the-badge)](https://github.com/neubofy/Remote-Manager/actions)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-green.svg?style=for-the-badge)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%20--%2016-orange.svg?style=for-the-badge&logo=android)](https://github.com/neubofy/Remote-Manager)

A lightweight, powerful, and secure cloud storage manager for Android, powered by **Rclone**.

---

## ✨ Features

- 📁 **Cloud File Management**: Browse, upload, download, move, rename, and delete files on 40+ cloud providers (Google Drive, OneDrive, Dropbox, Nextcloud, S3, WebDAV, SFTP, and more).
- 🔄 **Safe Move Tasks**: Move local files to remote (or remote to local) with verified delete-after-copy protection.
- ⚡ **Real-Time Sync**: Background sync with customizable triggers, filters, MD5 checksum verification, and Wi-Fi-only rules.
- 🖼️ **Thumbnail Previews**: Fast, memory-efficient image thumbnails with persistent local disk caching.
- 🔐 **End-to-End Encryption**: Supports Rclone Crypt remotes for full 256-bit client-side encryption.
- 🚀 **High Performance**: Native 64-bit ARM (`arm64-v8a`) binary engine compiled directly from official Rclone sources.
- 📦 **In-App Updater**: Direct GitHub Releases update checks and one-tap installation.

---

## 📥 Download & Installation

Grab the latest universal APK from [GitHub Releases](https://github.com/neubofy/Remote-Manager/releases/latest):

- **File**: `RemoteManager_v1.0.0.apk`
- **Target OS**: Android 12 through Android 16
- **Architecture**: ARM64 (`arm64-v8a`)

---

## 🛠️ Building from Source

### Prerequisites
- JDK 17
- Go 1.24+
- Android SDK & NDK 25+

```bash
# Clone the repository
git clone https://github.com/neubofy/Remote-Manager.git
cd Remote-Manager

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 📜 License
This project is licensed under the [GNU General Public License v3.0](LICENSE).
