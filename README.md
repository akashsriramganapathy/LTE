# LibreTube (Local Edition)

A local-only Android client for YouTube, forked from LibreTube. This version removes all dependencies on Piped instances, ensuring all data (subscriptions, history, playlists) is stored and managed locally on the device using a Room database.

## Technical Overview

This fork diverges from the upstream LibreTube project by enforcing a strict "Local Mode."

*   **Network**: Piped API usage for account management, subscriptions, and playlists has been completely removed. Network requests are limited to media retrieval (streams, subtitles, DeArrow, SponsorBlock).
*   **Data Persistence**: All user data is persisted in a local SQLite database (via Android Room).
*   **Localization**: The application is English-only. All other `values-*` resource directories and locale-switching logic have been removed to reduce APK size and simplified maintenance.
*   **Import/Export**: Supports importing subscriptions from JSON/CSV and exporting the entire database to a local file.

## Features

### Core Functionality
*   **Local Subscription Management**: Subscribe to channels without an account. Subscriptions are stored in the local database.
*   **Local Playlists**: Create and manage mixed-content playlists (videos and audio) locally.
*   **Watch History**: Local SQlite-based watch history with search and individual item deletion.
*   **Background Sync**: Implements a `WorkManager` task to periodically update metadata (video counts, thumbnails) for bookmarked playlists.

### User Interface
*   **Material 3 Design**: UI implementation follows Material 3 guidelines.
*   **Layout**: Horizontal `RecyclerView` shelves for Home, Trending, and Library views.
*   **Navigation**: Simplified settings hierarchy with indexed search functionality.
*   **Video/Audio Toggle**: Inline switch in the player view to toggle between video rendering (ExoPlayer) and audio-only mode.

### Integrations
*   **DeArrow**: Client-side integration to fetch and display community-submitted titles and thumbnails.
*   **SponsorBlock**: Skips sponsored segments via the SponsorBlock API.
*   **Return YouTube Dislike**: Displays dislike counts using the RYD API.

### Utility
*   **Automated Backups**: Configurable daily backups of the app database to local storage.
*   **Log Viewer**: Internal log viewer for debugging runtime errors (Player, Downloader, General).
*   **Update Checker**: Checks this repository for new releases and supports background update polling.

## Credits

*   **Development**: This fork was architected and built using Artificial Intelligence.
*   Upstream: [LibreTube](https://github.com/libre-tube/LibreTube) by [Bnyro](https://github.com/Bnyro)
*   DeArrow Integration Logic
*   SponsorBlock API

## License

GNU General Public License v3.0