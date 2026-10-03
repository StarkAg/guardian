# Security Policy

## Reporting a vulnerability

If you find a security issue — especially anything that lets a command run **without** the correct secret code, bypass the sender allow-list, or leak location data to an unintended recipient — please report it privately rather than opening a public issue.

- Use GitHub's **"Report a vulnerability"** (Security → Advisories) on this repo, or
- Open an issue asking for a private contact and we'll follow up.

Please include the device, Android version, and clear reproduction steps. We aim to acknowledge reports promptly and will credit reporters who want it.

## Scope / threat model

Guardian's only remote channel is an SMS that must carry the owner's secret code. There is no server, account, or network listener. The things we care most about:

- The secret code must be required and verified on **every** command path.
- Commands must never trigger from a message that merely contains the code as a substring.
- Location replies must go only to the configured owner number when an allow-list is set.

Reports that undermine any of the above are high priority.
