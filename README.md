# ThaiLingo 🇹🇭

**ThaiLingo** is a complete, modern, Duolingo-style language learning application for learning Thai (English &rarr; Thai). The application is engineered with modern Android practices and is **100% offline-first**. Every single lesson, exercise, vocable card, statistic, and achievement operates natively on-device without calling external servers.

---

## 📸 Core Highlights
- **Gamified Roadmaps**: Cascading themed units with custom visual nodes, completion checklists, and star indicators in true Duolingo fashion.
- **Heart Life Support**: Interactive penalty system subtracting hearts on wrong answers, completely synced with the offline progress stores.
- **Microphone and Speaker play**: Bundles an offline Text-to-Speech (TTS) voice wrapper for phonetic reads and mock decibel voice waveforms representing pronunciation matching.
- **Local Card Library**: Flip and study 52 authentic vocab words grouped in 5 categories with localized audio, romanizations, and example phrases.
- **XP Shop Room**: Settle XP rewards to fully buy back depleted health hearts, or complete a fast flashcard review session to recover hearts for free.
- **Offline Streak Check**: Increments daily learning streak automatically upon complete lesson graduations based on local date comparisons.

---

## 🛠️ Architecture & Tech Stack

ThaiLingo is structured using **Logical Clean Architecture** packaged cleanly within the starter module:

```
                  ┌───────────────────────┐
                  │      UI / Compose     │
                  │   (Screens & Views)   │
                  └───────────┬───────────┘
                              ▼
                  ┌───────────────────────┐
                  │    ViewModel / State  │
                  │  (ViewModel/StateFlow)│
                  └───────────┬───────────┘
                              ▼
                  ┌───────────────────────┐
                  │     Domain Layer      │
                  │    (Models, Repos)    │
                  └───────────┬───────────┘
                              ▼
                  ┌───────────────────────┐
                  │      Data Layer       │
                  │ (Room, RepositoryImpl)│
                  └───────────────────────┘
```

### Layer Directories Breakdown
1. **`com.example.domain`**: Stores the pure platform models (`Models.kt`) and the core abstraction contracts (`Repository.kt`).
2. **`com.example.data`**: Manages underlying storage, contains local SQLite converters, mappings, and Room entities (`local/Entities.kt`), exposes reactive queries (`local/Daos.kt`), and manages the Single Source of Truth (`RepositoryImpl.kt`) which handles automatic catalog pre-population on first-launch.
3. **`com.example.core.common`**: Injects offline voice systems (`ThaiTtsHelper.kt`) and binds dependencies securely via a central application locator (`ServiceLocator.kt`) to ensure fast and fail-safe compilation.
4. **`com.example.feature`**: Anchors Jetpack Compose screens (Onboarding, Roadmap Home, Active Challenges, Practice Arena, Profile settings) and coordinates state controllers (ViewModels).

---

## 🗄️ Offline Data & Pre-population Notes

ThaiLingo loads **52 handpicked Thai vocabularies** across **5 thematic categories** directly into the local SQLite database when the app is first started:
- 💬 **Greetings**: Essentials such as hello, goodbye, thank you, and sorry.
- 🍲 **Food & Drinks**: Rice, water, papaya salad, green curry, and delicious spicy traits.
- 💵 **Numbers & Trade**: Counting 1-5, currencies (Baht), cheap/expensive traits, and shopping terms.
- ✈️ **Travel**: Airport, railway stations, restroom locations, and tuk-tuk direction helpers.
- 👨‍👩‍👧‍👦 **Family**: Father, mother, brothers, sisters, and love descriptions.

### Gamified Challenges Built-In
Every lesson groups a 5-step exercise workout of multiple-choice cards, typing boxes, audio playback triggers, matching grids (balancing English tokens against Thai scripts under reactive flags), and speaking cards.

---

## 🚀 Building & Testing

### Running Tests Locally
To verify local JVM Robolectric unit tests and Roborazzi screenshot verification suites, run:
```bash
gradle :app:testDebugUnitTest
```
To capture and record updated visual baseline test assets:
```bash
gradle :app:recordRoborazziDebug
```

### Compiling APKs in Production
To bundle a standard Debug APK or compile signed production App Bundles (AAB):
```bash
# Debug APK path: app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleDebug

# Release AAB path: app/build/outputs/bundle/release/app-release.aab
gradle :app:bundleRelease
```
