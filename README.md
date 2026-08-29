# DevForge Pro - Android AI Mobile IDE & APK Builder

**DevForge Pro** is an Android application development studio built using Kotlin and Jetpack Compose. It connects directly with GitHub and Cloud AI services to enable repository management, live AI code modifications, and cloud APK build compilation via GitHub Actions.

## Creator
Created by **FourgeAI LABS**  
GitHub: [https://github.com/fourgeailabs](https://github.com/fourgeailabs)

## App GitHub Repository
GitHub Repository: [https://github.com/fourgeailabs/DevForge](https://github.com/fourgeailabs/DevForge)

## Recent Updates

### Version 1.14.00
- **Cloud AI Tool & Service Selector**: Added interactive Cloud AI provider selector in Settings supporting Google Gemini, OpenAI ChatGPT, Anthropic Claude, xAI Grok, DeepSeek, and Custom Cloud AI with per-service API key management.
- **Generic Cloud AI Rebranding**: Standardized all AI completion predictions, code review tools, and UI indicators under generic Cloud AI branding across the application.

### Version 1.13.00
- **Interactive Code Editor & Build Engine**: Integrated multi-stage compilation flow (saving source, AST parsing, kotlinc, R8 DEX transpilation, and signed debug APK packaging) in `EditorScreen`.
- **Live Terminal Console**: Real-time console displaying compilation logs and diagnostic messages.
- **Direct APK Download & Remote Triggering**: Download triggers for compiled APKs and automatic remote GitHub Actions workflow triggering.
- **Custom Instructions Alignment**: Versioning set to `1.13.00` (versionCode `14`), closed-by-default release notes dropdown accordion, and Creator links to FourgeAI LABS (`https://github.com/fourgeailabs`).

### Version 1.12.00
- **In-Card Active Action Processing Display**: Directly displays active GitHub Actions / workflow build status inside each repository card on the main dashboard.
- **Live Elapsed & Remaining Time Indicators**: Embedded real-time ticker showing elapsed processing duration (e.g. `Elapsed: 1m 24s`) and estimated remaining build time (e.g. `~1m 36s remaining`).

### Version 1.11.00
- **Banner Cleanup**: Completely removed the "Automate AI Studio to Mobile APKs" banner card from the layout.

### Version 1.10.00
- **UI Streamlining**: Removed the "Paste Public Creator / Repo Link" button from empty and error state dashboard views to provide a clean repository list layout.

### Version 1.09.00
- **Dashboard Layout Streamlining**: Removed the inline "Explore Repo or Creator Link" card from the main dashboard for a cleaner repository list interface.
- **TopAppBar Globe Link Preservation**: Retained the Globe icon button in the top navigation bar, keeping the public repo / creator URL search dialog fully functional.

### Version 1.08.00
- **Google AI Studio Settings Link**: Added a direct external link to Google AI Studio (`ai.studio`) in the Settings menu under Tools & External Resources.
- **UI Clean Up**: Removed AI Studio helper banners and extraneous textual references from the main dashboard.

### Version 1.07.00
- **GitHub Release Asset API Endpoint Integration**: Replaced direct web browser download URLs with the official GitHub REST API release asset endpoint (`/repos/{owner}/{repo}/releases/assets/{asset_id}`) using the `Accept: application/octet-stream` header. This prevents HTTP 404 Not Found errors on release assets across public & private repositories.
- **OkHttp Cross-Host Header Preservation**: Preserved `Authorization` headers on requests directly targeting `github.com` while maintaining automatic header removal when redirecting off to third-party storage hosts (S3, Azure Blob Storage).
- **Actionable HTTP 404 Error Diagnostics**: Replaced generic 404 exception strings with clear, step-by-step guidance explaining expired build artifacts, missing `.github/workflows/build.yml` files, or PAT permission requirements.

### Version 1.06.00
- **Background Thread Coroutine Execution**: Refactored `ApkInstaller` network streaming, Zip extraction, and file operations to run explicitly on `Dispatchers.IO` background threads. This eliminates `NetworkOnMainThreadException` during artifact downloads.
- **Main Thread Installer Dispatch**: Ensured package installer intent dispatches (`ACTION_VIEW`) run cleanly on `Dispatchers.Main`.
- **Diagnostic Exception Messaging**: Enhanced error formatting (`localizedMessage ?: message ?: simpleName`) so exceptions present meaningful, actionable details instead of `null` error cards.

### Version 1.05.00
- **S3/Azure Cross-Host Redirect Fix**: Added a network interceptor to OkHttpClient that strips `Authorization` headers when OkHttp follows 302 redirects off `api.github.com` to Azure/S3 storage hosts. This prevents Azure Blob Storage and AWS S3 from rejecting pre-signed SAS URLs with 400 Bad Request or 403 Forbidden errors.
- **Multi-Phase APK Extraction Engine**: Upgraded `ApkInstaller` with `ZipFile`, `ZipInputStream`, and nested ZIP archive search to reliably extract `.apk` binaries from any GitHub Actions artifact layout.
- **Android Package Integrity Verification**: Integrated `PackageManager.getPackageArchiveInfo` validation to inspect and confirm APK package signatures before passing them to the system installer.
- **Diagnostic Download Feedback**: Detailed file size logging and error snippet inspection if artifact downloads fail or return API error responses.

### Version 1.04.00
- **Prioritized Pre-Built Unzipped APK Installer**: Automatically scans GitHub Releases for direct `.apk` assets uploaded by creators. If an unzipped `.apk` is available, DevForge Pro prioritizes it with a highlighted banner at the top of the repository screen, allowing 1-tap direct download and installation.
- **Purged Mandatory Login Screen**: Completely removed mandatory login gating. The app opens directly to the Dashboard home screen with full guest capabilities.
- **Settings API Key Hub**: Integrated GitHub Personal Access Token authentication directly in the Settings menu with step-by-step creation guidance and connection status.
- **Top Home Search Bar**: Moved the repository and creator link search bar directly to the top of the home screen for instant access.

### Version 1.03.00
- **Public Repository & Creator Explorer**: Access public GitHub repositories or creator profile pages by simply pasting a GitHub link or creator handle (e.g. `https://github.com/fourgeailabs` or `fourgeailabs/DevForge`) without needing a PAT token.
- **Repository Workflow Integration**: Full workflow guidance for GitHub repositories. Export apps directly to GitHub, then monitor, trigger, and install APK builds inside DevForge Pro.
- **Robust ZipFile Extraction Engine**: Upgraded stream-based APK installer to use local temp files and `ZipFile` extraction to resolve unpacking errors across all Android devices.
- **Accurate Elapsed Build Timer**: Corrected workflow run duration calculations using `run_started_at` timestamps for re-runs.

### Version 1.02.00
- **Pull to Refresh**: Swipe down on the repository actions screen to manually refresh build runs and artifact availability status from GitHub.
- **Direct Build Rerun**: Dedicated 1-tap re-run button for the most recent workflow run on GitHub Actions.
- **Gemini AI Build Completion Estimates**: Live completion prediction powered by Gemini 3.5 Flash.
- **Automated ZIP Artifact Downloader & Installer**: Automatically scans and extracts APK artifacts directly from repository-level ZIP files.

### Version 1.01.00
- **GitHub OAuth / PAT Authentication**: Dedicated login screen to authenticate directly using GitHub Personal Access Tokens.
- **Repository Browser**: Modern UI for browsing user GitHub repositories directly inside the mobile app.
- **GitHub Actions APK Builder**: Directly trigger GitHub Actions `build.yml` workflows to compile APKs in the cloud.
- **Live Build Progress & Status**: Real-time progress monitoring of workflow jobs and statuses (`queued`, `in_progress`, `completed`).
- **APK Download & Auto Sideloading**: Download built APK artifacts directly to the device and initiate seamless package installation.
- **Bring Your Own Key (BYOK) Gemini AI**: Live AI code analysis and auto-fixing using Gemini API.
- **About & What's New Sections**: Added in Settings with versioning and release notes.

---

## Features
- **GitHub Repositories**: Browse private and public repositories or explore public creators.
- **AI Studio Integration**: Automatically track and compile APKs for exported Google AI Studio projects.
- **Cloud APK Compilation**: Trigger GitHub Actions workflows for automated APK creation.
- **Automated Sideloading**: Extract APK artifacts and trigger native Android installer.
- **AI Coding Companion**: Powered by Gemini 3.5 Flash for code reviews and auto-repairs.
- **M3 Dark / Light Theme**: Centralized Material 3 styling with custom components.
