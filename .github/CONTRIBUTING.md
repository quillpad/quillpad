# Contributing to Quillpad

Thanks for your interest in contributing to Quillpad! 

Quillpad is relied upon by thousands of users every day to safely store their most important notes and ideas. It is absolutely critical that their data does not get corrupted or damaged by new changes. Since carefully reviewing Pull Requests to prevent regressions takes a significant amount of maintainer time and effort, **please do not create PRs without thoroughly reviewing and testing the code changes yourself first.**

### 🤖 AI Assisted Code & Vibe Coding

This requirement for careful self-review is especially true for contributions that are "vibe coded" or aided by AI tools. We fully welcome these contributions! However, to ensure the project remains stable and maintainable, please make sure:
- The changes are properly tested against regressions.
- You have a personal, working understanding of Kotlin. You must be able to understand, explain, and maintain the code that the AI generates.

## 💻 Developer Guidance

### General Project Structure

Quillpad is an Android application built using Kotlin and Gradle. The main source code is located in the `app/` directory:
- `app/src/main/java/org/qosp/notes/` - Contains the core Kotlin source code, separated by features such as data (Room database, Sync), UI, and domain logic.
- `app/src/main/res/` - Contains Android resources such as layouts, drawables, and values.

### Code Style

This project includes an `.editorconfig` file at the root directory to maintain consistent coding styles.
- **Android Studio / IntelliJ**: Supports `.editorconfig` defaults natively out of the box.
- **VS Code**: You will need to install the [EditorConfig for VS Code](https://marketplace.visualstudio.com/items?itemName=EditorConfig.EditorConfig) extension.

### Running the Project

The project uses the standard Gradle build system. To run it locally:
1. Open the project in Android Studio.
2. Let Gradle sync and resolve all dependencies.
3. Select the `app` configuration.
4. Click the Run button (or use `Shift + F10`) to build and deploy to your connected Android device or emulator.

Alternatively, you can build the project from the command line:

```bash
./gradlew assembleDebug
```

## 🌎 Translations

Please help with translations using [Weblate](https://toolate.othing.xyz/projects/quillpad/).

<a href="https://toolate.othing.xyz/projects/quillpad/">
<img alt="Translation status" src="https://toolate.othing.xyz/widget/quillpad/multi-auto.svg"/>
</a>

Right now we are only looking for translation contributions through Weblate. Manual pull requests for string files are no longer accepted.
