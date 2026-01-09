# LTE (LibreTube Edition)

A strictly local-only Android client for YouTube, built on modern Android tech. This project is a hard fork of LibreTube that removes all Piped instance dependencies in favor of a completely offline, device-centric database approach.

## Key Features

### 🚀 Modern Tech Stack
*   **Jetpack Compose Player**: The video player has been completely rewritten from scratch using Jetpack Compose, `AnchorDraggable`, and `Media3`/`ExoPlayer` for a fluid, gesture-driven experience.
*   **Local-First Architecture**: All subscriptions, playlists, and watch history are stored in a local Room database. No account required, no external server tracking.
*   **Material 3**: Fully compliant Material 3 UI with dynamic colors and modern components.

### 📺 Media Experience
*   **DeArrow Integration**: Built-in support for crowdsourced titles and thumbnails to remove clickbait.
*   **SponsorBlock**: Automatically skips sponsored segments.
*   **Audio/Video Toggle**: Seamlessly switch between video and audio-only modes directly from the player.
*   **Background Play**: Robust background playback service with media notification controls.

### 🛠️ Advanced Tools
*   **Log Viewer**: Integrated in-app log viewer for real-time debugging.
*   **Database Backup**: Automatic daily backups of your library and history.
*   **English Only**: Stripped of all localization to strictly minimize APK size and complexity.

## Project Structure
*   **Repo**: [https://github.com/akashsriramganapathy/LTE](https://github.com/akashsriramganapathy/LTE)
*   **Status**: Active Development (Experimental)

## Credits
*   **Development**: Architected and built with standard-setting AI coding workflows.
*   **Upstream**: Forked from [LibreTube](https://github.com/libre-tube/LibreTube).