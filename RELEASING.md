# Releasing

## Overview

Domain-api uses automated publishing to Maven Central via GitHub Actions. The release process is triggered by creating a tag in semver format on the main branch:

- **Full release** (all modules): Tag with `v*` (e.g., `v0.10.4`)
- **lib-kfsm only**: Tag with `lib-kfsm-v*` (e.g., `lib-kfsm-v0.10.5`)
- **lib-kfsm-v2 only**: Tag with `lib-kfsm-v2-v*` (e.g., `lib-kfsm-v2-v2.0.0`)

## Prerequisites

Before releasing, ensure you have:
- Write access to the repository
- Access to the required GitHub secrets:
  - `SONATYPE_CENTRAL_USERNAME`
  - `SONATYPE_CENTRAL_PASSWORD`
  - `GPG_SECRET_KEY`
  - `GPG_SECRET_PASSPHRASE`

## Release Steps

### 1. Prepare the Release

1. Set the release version:

    ```sh
    export RELEASE_VERSION=A.B.C
    ```

2. Create a release branch:

    ```sh
    git checkout -b release/$RELEASE_VERSION
    ```

3. Update `CHANGELOG.md` with changes since the last release. Follow the existing `CHANGELOG.md` format, which is derived from [this guide](https://keepachangelog.com/en/1.0.0/)

4. Update the version in `gradle.properties`:

    ```sh
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      gradle.properties
    ```

5. Commit and push the release branch:

    ```sh
    git add .
    git commit -m "Prepare for release $RELEASE_VERSION"
    git push origin release/$RELEASE_VERSION
    ```

6. Create a pull request to merge the release branch into main:

    ```sh
    gh pr create --title "Release $RELEASE_VERSION" --body "Release version $RELEASE_VERSION"
    ```

7. Review and merge the pull request to main

### 2. Create and Push the Release Tag

Once the release PR is merged to main:

1. Pull the latest changes from main:

    ```sh
    git checkout main
    git pull origin main
    ```

2. Create a tag in semver format (must start with "v"):

    ```sh
    git tag -a v$RELEASE_VERSION -m "Release version $RELEASE_VERSION"
    git push origin v$RELEASE_VERSION
    ```

### 3. Automated Publishing

Once the tag is pushed, the [Publish to Maven Central](https://github.com/block/domain-api/actions/workflows/publish.yml) workflow will automatically:

1. Build the artifacts:
   - `xyz.block.domainapi:domain-api:$version`
   - `xyz.block.domainapi:domain-api-kfsm:$version`
   - `xyz.block.domainapi:domain-api-kfsm-v2:$version`

2. Sign the artifacts with GPG

3. Publish to Maven Central via Sonatype

4. Generate and publish documentation to GitHub Pages

**Note**: It can take 10-30 minutes for artifacts to appear on Maven Central after successful publishing.

## Releasing lib-kfsm Only

When a new version of KFSM is released, you may want to release only the `lib-kfsm` module without bumping the core `lib` version.

The `lib-kfsm` module has its own version and depends on a specific published version of `domain-api`, configured in `lib-kfsm/gradle.properties`:

- `domainApiVersion`: The published version of `domain-api` that this module depends on

### Steps

1. Update the KFSM version in `gradle/libs.versions.toml`

2. Update `lib-kfsm/gradle.properties`:

    ```sh
    export LIB_KFSM_VERSION=A.B.C
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$LIB_KFSM_VERSION/g" \
      lib-kfsm/gradle.properties
    ```

   Also ensure `domainApiVersion` points to the correct published version of `domain-api`.

3. Update `CHANGELOG.md` with the KFSM update

4. Commit and push the changes, then create and push a tag with the `lib-kfsm-v` prefix:

    ```sh
    export LIB_KFSM_VERSION=A.B.C
    git tag -a lib-kfsm-v$LIB_KFSM_VERSION -m "Release lib-kfsm version $LIB_KFSM_VERSION"
    git push origin lib-kfsm-v$LIB_KFSM_VERSION
    ```

This will publish only `xyz.block.domainapi:domain-api-kfsm:$version` to Maven Central (without regenerating docs).

**Note**: The publish workflow validates that the tag version matches `VERSION_NAME` in `lib-kfsm/gradle.properties`. If they don't match, the workflow will fail.

## Releasing lib-kfsm-v2 Only

For releasing the `lib-kfsm-v2` module independently (e.g., for KFSM 2.x compatibility updates):

### Steps

1. Update the `kfsm2` version in `gradle/libs.versions.toml` to the new KFSM version

2. Make any required code changes in `lib-kfsm-v2`

3. Update `lib-kfsm-v2/gradle.properties`:

    ```sh
    export LIB_KFSM_V2_VERSION=A.B.C
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$LIB_KFSM_V2_VERSION/g" \
      lib-kfsm-v2/gradle.properties
    ```

   Also ensure `domainApiVersion` points to a compatible published `domain-api` version.

4. Update `CHANGELOG.md` with the changes

5. Commit and push the changes, then create and push a tag with the `lib-kfsm-v2-v` prefix:

    ```sh
    export LIB_KFSM_V2_VERSION=A.B.C
    git tag -a lib-kfsm-v2-v$LIB_KFSM_V2_VERSION -m "Release lib-kfsm-v2 version $LIB_KFSM_V2_VERSION"
    git push origin lib-kfsm-v2-v$LIB_KFSM_V2_VERSION
    ```

**Note**: The publish workflow validates that the tag version matches `VERSION_NAME` in `lib-kfsm-v2/gradle.properties`. If they don't match, the workflow will fail.

### Coexistence with lib-kfsm

The `lib-kfsm-v2` module uses a different package namespace (`xyz.block.domainapi.kfsm.v2.util`) allowing both versions to coexist on the classpath. Users can use both `domain-api-kfsm:1.x` (with KFSM 1.x) and `domain-api-kfsm-v2:2.x` (with KFSM 2.x) in the same project.

### 4. Create GitHub Release

1. Go to [GitHub Releases](https://github.com/block/domain-api/releases/new)
2. Select the tag you just created (`v$RELEASE_VERSION`)
3. Copy the release notes from `CHANGELOG.md` into the release description
4. Publish the release

### 5. Prepare for Next Development Version

1. Create a new branch for the next development version:

    ```sh
    export NEXT_VERSION=A.B.D-SNAPSHOT
    git checkout -b next-version/$NEXT_VERSION
    ```

2. Update the version in `gradle.properties` to the next snapshot version:

    ```sh
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/g" \
      gradle.properties
    ```

3. Commit and push the changes:

    ```sh
    git add .
    git commit -m "Prepare next development version"
    git push origin next-version/$NEXT_VERSION
    ```

4. Create a pull request to merge the next version branch into main:

    ```sh
    gh pr create --title "Prepare next development version" --body "Update version to $NEXT_VERSION"
    ```

5. Review and merge the pull request

## Troubleshooting

### Publishing Failures

- If the GitHub Action fails, check the workflow logs for specific error messages
- Common issues include:
  - Invalid GPG key or passphrase
  - Incorrect Sonatype credentials
  - Version conflicts (if the version was already published)
  - Network connectivity issues

### Manual Intervention

If the automated publishing fails and you need to manually intervene:

1. Check the [Sonatype Nexus](https://oss.sonatype.org/) staging repository
2. Drop any failed artifacts from the staging repository
3. Fix the issue and re-tag the release (delete the old tag first)
4. Re-run the workflow

### Access Issues

If you don't have access to the required secrets or Sonatype account, contact the project maintainers.

## Release Artifacts

Each release includes:

- **Core Library**: `xyz.block.domainapi:domain-api:$version`
  - Main JAR with compiled classes
  - Sources JAR
  - Javadoc JAR
  - POM file

- **KFSM Utilities**: `xyz.block.domainapi:domain-api-kfsm:$version`
  - Package: `xyz.block.domainapi.util`
  - Main JAR with compiled classes
  - Sources JAR
  - Javadoc JAR
  - POM file

- **KFSM v2 Utilities**: `xyz.block.domainapi:domain-api-kfsm-v2:$version`
  - Package: `xyz.block.domainapi.kfsm.v2.util`
  - For KFSM 2.x compatibility
  - Can coexist with `domain-api-kfsm` in the same project
  - Main JAR with compiled classes
  - Sources JAR
  - Javadoc JAR
  - POM file

All artifacts are signed with GPG and published to Maven Central.
