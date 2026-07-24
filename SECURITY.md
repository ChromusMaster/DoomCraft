# Security Policy

## Supported Versions

DoomCraft follows the Minecraft Java Edition versioning scheme (Year.Major). We actively maintain the latest stable branch and provide critical security backports for the previous major release.

| Version          | Supported          | Notes                                                                 |
| ---------------- | ------------------ | --------------------------------------------------------------------- |
| 26.2             | :white_check_mark: | **Active development** – all security patches and updates.            |
| Snapshot (26.3)  | :construction:     | Supported for testing, but may contain experimental security flaws.   |

## Scope of Security

DoomCraft operates by spawning a **native LZDoom process** managed by a C++ bridge and a Java mod layer. Our security policy covers:
- The Java mod code (`DoomCraft.java`, network handlers, etc.).
- The C++ bridge code (JNI layer, process management, IPC).
- Build pipelines (CMake/Gradle) and dependency manifests.
- File validation for WADs and configuration files.

**Out of Scope:** Vulnerabilities in the LZDoom engine itself, malicious WAD files (we recommend only using trusted .wad files), or vanilla Minecraft server/client exploits.

## Reporting a Vulnerability

We take security bugs in DoomCraft seriously and appreciate your efforts to disclose them responsibly.

### How to Report
- **Preferred Method:** Use the **"Report a vulnerability"** button on the GitHub repository's **Security** tab. This ensures the report is private and accessible only to the maintainers.
- **Alternative (if GitHub is unavailable):** Open a **blank Issue** with `[SECURITY]` in the title and prefix your description with `// PRIVATE`. We will immediately mark it as private and reach out via email. 

### What to Include
To help us triage effectively, please include:
1. The **exact version** of DoomCraft and your OS (Windows/Linux/macOS).
2. Steps to reproduce the vulnerability (minimalistic, if possible).
3. Logs, crash reports, or proof-of-concept code.
4. Your assessment of the potential impact (e.g., RCE, heap corruption, memory leak, sandbox escape).

### Our Response Timeline
- **Acknowledgment:** We will confirm receipt within **48 hours**.
- **Triage:** We will validate the issue and assess severity within **5 business days**.
- **Fix:** 
  - Critical vulnerabilities (CVSS ≥ 7.0) → Patch released within **2 weeks** (or immediate snapshot).
  - Moderate/Low severity → Scheduled for the next regular update.
- **Public Disclosure:** We will credit you in the release notes unless you prefer to remain anonymous.

## Responsible Disclosure Guidelines
- Please **do not** disclose the vulnerability publicly (e.g., in Discord, Issues, or Reddit) until we have released a fix and given you a 48-hour window to publish your analysis.
- We will never pursue legal action against researchers acting in good faith and adhering to these guidelines.

## Build Artifact Safety
All official releases are built via GitHub Actions using our hardened Gradle/CMake pipelines. If you are building from source, ensure your toolchain (GCC/Clang, JDK 21+) is up-to-date to avoid compiler-induced vulnerabilities.

---

*Last updated: July 24, 2026*
