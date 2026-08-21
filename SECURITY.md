# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Bus-Hop, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please email security concerns to the maintainer via GitHub:
1. Go to https://github.com/icemedica341
2. Use the "Sponsor" button to find contact information
3. Or open a private security advisory: https://github.com/icemedica341/Bus-Hop/security/advisories/new

## What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

## Response Timeline

- Acknowledgment within 48 hours
- Assessment within 1 week
- Fix or mitigation within 2 weeks (for confirmed vulnerabilities)

## Scope

Bus-Hop is a client-side Android app that fetches public bus arrival data from `arrivelah2.busrouter.sg`. The app:

- Collects NO user data
- Has NO user accounts or authentication
- Makes NO analytics or tracking calls
- Stores ONLY user-selected bus stops locally (DataStore)

Security issues are most likely to be found in:

- Network communication (certificate pinning bypass)
- Local data storage
- The self-update mechanism (APK download from GitHub releases)

## Supported Versions

| Version | Supported |
| --- | --- |
| Latest | Yes |
| Older | No |

Bus-Hop is currently unmaintained. Security fixes are not guaranteed.
