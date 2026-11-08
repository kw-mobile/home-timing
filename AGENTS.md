# Team working agreements

- Read [README.md](README.md), [docs/SETUP.md](docs/SETUP.md), and [docs/MVP.md](docs/MVP.md) before changing the project.
- Build understanding: this repository contains a Java + XML Android app. Implement only the scope agreed in docs/MVP.md; this bootstrap does not implement Firebase rules or product flows.
- Use the checked-in Gradle wrapper with JDK 17. Never commit local SDK/JDK paths, credentials, signing files, or IDE state.
- Keep dependency/plugin/SDK versions in gradle/libs.versions.toml. Gradle's version/checksum live in gradle/wrapper/gradle-wrapper.properties. Do not accept IDE upgrade prompts on feature branches.
- When changing dependencies, update the catalog and regenerate app/gradle.lockfile with the command in docs/SETUP.md; never hand-edit lock entries.
- Keep Activity/Fragment rendering and input separate from ViewModel, repositories, and pure Java domain rules. No direct Firebase or Retrofit calls in UI classes.
- Build and lint with `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Report NO-SOURCE honestly; a bootstrap build does not prove feature behavior or Firebase security.
- Follow [CONTRIBUTING.md](CONTRIBUTING.md) for branches and PRs. Never merge or change repository protections without explicit authorization.
