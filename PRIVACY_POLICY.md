# Privacy Policy - DevForge Pro

**Effective Date:** August 31, 2026  
**Last Updated:** August 31, 2026  
**Developer / Creator:** FourgeAI LABS ([https://github.com/fourgeailabs](https://github.com/fourgeailabs))  
**App Repository:** [https://github.com/fourgeailabs/DevForge](https://github.com/fourgeailabs/DevForge)

---

## 1. Overview & Core Philosophy

**DevForge Pro** is an open, privacy-first mobile development studio designed to empower Android developers to manage GitHub repositories, edit code, and trigger/monitor GitHub Actions APK build workflows.

We believe that your source code, authentication tokens, and API keys belong strictly to you. **DevForge Pro does not run private analytics servers, advertising networks, or external telemetry systems.** All operations are conducted locally on your Android device or through direct, authenticated connections to the services you explicitly choose to use (e.g., GitHub, Cloud AI providers).

---

## 2. Information Collection and Storage

### A. Personal Access Tokens (PAT) & Credentials
- **Local Device Storage:** When you input a GitHub Personal Access Token (PAT) to access private repositories or trigger GitHub Actions workflows, this token is stored exclusively on your device using Android's encrypted Jetpack DataStore preferences sandbox.
- **Zero Intermediary Servers:** DevForge Pro never transmits, logs, or proxies your GitHub PAT through any third-party or FourgeAI LABS server. All requests go directly to `api.github.com` via secure HTTPS/TLS.

### B. Bring-Your-Own-Key (BYOK) Cloud AI Keys
- **Local Storage:** API keys entered for Cloud AI models (such as Google Gemini, OpenAI ChatGPT, Anthropic Claude, xAI Grok, DeepSeek, or Custom Cloud AI endpoints) are stored locally in application private storage.
- **Direct Dispatch:** AI code completions, syntax diagnostics, and build predictions are sent directly from your device to the respective provider's official API endpoint over HTTPS.

### C. Source Code & Repository Content
- **Sandboxed Cache:** Files viewed or modified in the built-in code editor are stored locally within the application sandbox.
- **No Third-Party Access:** Your repository contents, branch data, and commit history are never shared with anyone other than the authenticated GitHub account you have configured.

---

## 3. Android System Permissions & Purpose

DevForge Pro requests only the minimal set of permissions strictly necessary for core functionality:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.INTERNET` | Communicates directly with GitHub REST API (`api.github.com`), queries workflow statuses, streams release assets, and makes user-initiated Cloud AI requests. |
| `android.permission.REQUEST_INSTALL_PACKAGES` | Allows the user to install newly compiled APK binaries built via GitHub Actions workflows or downloaded via in-app updates directly to their Android device. Package installation always requires explicit user confirmation. |
| `androidx.core.content.FileProvider` | Grants secure, scoped temporary URI access to Android's native `PackageInstaller` for installing verified `.apk` archives without exposing unrestricted device file system access. |

---

## 4. Third-Party Services & External Links

When using DevForge Pro, you interact with third-party developer platforms whose respective terms and privacy statements apply:

- **GitHub (Microsoft Corporation):** Used for repository browsing, Personal Access Token authentication, release asset downloads, and GitHub Actions workflow execution.  
  *GitHub Privacy Statement:* [https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement](https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement)
- **Google AI Studio / Gemini API (Google LLC):** Optional BYOK AI model provider.  
  *Google Privacy Policy:* [https://policies.google.com/privacy](https://policies.google.com/privacy)
- **Other AI Providers (OpenAI, Anthropic, xAI, DeepSeek):** Governed by the respective platform policies when you configure BYOK keys.

---

## 5. Data Retention & Full Control

- **Immediate Revocation & Disconnect:** You can disconnect your GitHub account and delete all stored PAT keys at any time by tapping **"Disconnect"** in the Settings menu.
- **Key Removal:** Clearing your AI API key input field in Settings immediately wipes it from device storage.
- **App Uninstall / Clear Data:** Uninstalling the application or using Android's "Clear Data" feature completely and permanently removes all stored preferences, tokens, and cached project files from the device.

---

## 6. Children's Privacy

DevForge Pro does not knowingly collect or solicit personal information from children under the age of 13. The application is a software developer utility intended for developers of all ages.

---

## 7. Changes to This Privacy Policy

If we update this Privacy Policy, the revised version will be reflected in both this repository documentation (`PRIVACY_POLICY.md`) and within the in-app Privacy Policy viewer in DevForge Pro settings, with the updated Effective Date noted at the top.

---

## 8. Contact & Creator Information

For any questions, concerns, or feedback regarding this Privacy Policy or DevForge Pro:
- **Organization:** FourgeAI LABS
- **GitHub:** [https://github.com/fourgeailabs](https://github.com/fourgeailabs)
- **Project Repository:** [https://github.com/fourgeailabs/DevForge](https://github.com/fourgeailabs/DevForge)
