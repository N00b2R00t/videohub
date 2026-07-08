# VideoHub by Noobie 🚀

Welcome to **VideoHub**, a high-performance, modern, and beautiful media downloading and management companion designed with Material 3. Guided by our friendly mascot mascot **Noobie**, VideoHub provides a seamless offline media tracking experience.

---

## ✨ Features

- 📱 **Modern Material 3 Interface**: Beautifully themed with high-contrast elements, responsive grids, and adaptive layouts tailored for compact and expanded displays.
- 🤖 **Smart Search with Noobie**: Paste any URL or query, and let Noobie help guide your media tracking and categorization.
- 🗂️ **Dynamic Categorization**: Seamlessly filter and browse your media across multiple categories, including **Featured**, **Music**, **Video**, and **Social**.
- 📥 **Offline-First Persistence**: Integrated with a robust, local **Room database** to manage ongoing, paused, and completed downloads efficiently.
- ⚙️ **Smooth Control Center**: Easily pause, resume, cancel, or delete downloads with visual progress indicators and real-time status updates.
- 🎨 **Unique Aesthetic**: Featuring custom vector adaptive launcher icons and geometric card layouts for a truly polished look.

---

## 🛠️ Architecture & Tech Stack

VideoHub is built adhering to Google's recommended modern Android development practices:

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture Pattern**: MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Asynchronous Flow**: Kotlin Coroutines & StateFlow
- **Database / Local Storage**: Room Database with KSP (Kotlin Symbol Processing)
- **Networking**: Retrofit & Ktor for robust external API connectivity
- **Image Loading**: Coil
- **Testing & Verification**:
  - **Local JVM Testing**: Robolectric for rapid Critical User Journey validation.
  - **Visual Verification**: Roborazzi screenshot and regression tests.

---

## 🚀 Getting Started

### Prerequisites

- Android SDK 34 (Compile & Target)
- JDK 17
- Gradle (Kotlin DSL)

### Building the Project

To build the debug APK directly, use:

```bash
gradle assembleDebug
```

### Running Tests

To execute the unit and Robolectric integration tests, use:

```bash
gradle :app:testDebugUnitTest
```

---

## 🎨 Design Theme & Identity

VideoHub utilizes a custom-tailored **Noobie Blue** color palette, combining high-contrast slate colors with vibrant highlights:
- **Primary Color**: Deep Blue `#001D36` / `#004A7F`
- **Container Tones**: Soft Ice Blue `#D3E4FF` for active elements
- **Mascot Logo**: A friendly, flat vector little helper robot with bright digital eyes and a green beginner leaf sprout, embodying an approachable and clean branding language.

---

## 📜 License

Created and maintained with ❤️ by the **Noobie** team. All rights reserved.
