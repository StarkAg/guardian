# Contributing to Guardian

Thanks for helping out! Guardian is a small, readable Kotlin codebase and contributions of all sizes are welcome.

## Ways to help
- 🐛 **Report bugs** — open an issue with your device model, Android version, and steps.
- 💡 **Suggest features** — open an issue describing the use case.
- 🧑‍💻 **Send a PR** — pick an open issue (look for `good first issue`) or propose a change.
- 🌍 **Translate** — add string resources for your language.
- 📖 **Improve docs** — typos and clarifications count.

## Development setup
1. Install Android Studio (JDK 17) and the Android SDK.
2. Clone the repo and open the folder in Android Studio, or build from the CLI:
   ```bash
   ./gradlew assembleDebug
   ```
3. `local.properties` is generated automatically and is git-ignored — don't commit it.

## Pull-request guidelines
- Keep PRs focused; one logical change per PR.
- Match the existing Kotlin style (the code favors small objects, coroutines, and clear comments explaining *why*).
- Describe what you changed and how you tested it.
- If you add a permission or a privileged action, explain why it's necessary.

## Code of conduct & responsible use
Guardian exists for lawful anti-theft and **consensual** family-safety use. Contributions that add covert-surveillance or stalkerware capabilities (hiding the app from its own user, silently exfiltrating data to third parties, defeating consent) will be declined. Be kind in issues and reviews.

Happy hacking! ⭐
