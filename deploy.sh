#!/bin/sh

output_error() {
  echo >&2 -e "🔴 ${1}: ${2}"
}

output_success() {
  echo -e "🟢 ${1}: ${2}"
}

output_awating_action() {
  echo -e "🔵 ${1} ${2}"
}

git_require_on_main_branch() {
    if [ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]
    then
      output_error "GIT" "not on 'main' branch"
      exit 1
    fi

    output_success "GIT" "on main branch"
}

git_require_clean_work_tree () {
    # Update the index
    git update-index -q --ignore-submodules --refresh
    err=0

    # Disallow unstaged changes in the working tree
    if ! git diff-files --quiet --ignore-submodules --
    then
        output_error "GIT: You have unstaged changes."
        git diff-files --name-status -r --ignore-submodules -- >&2
        err=1
    fi

    # Disallow uncommitted changes in the index
    if ! git diff-index --cached --quiet HEAD --ignore-submodules --
    then
        output_error "GIT: Your index contains uncommitted changes."
        git diff-index --cached --name-status -r --ignore-submodules HEAD -- >&2
        err=1
    fi

    if [ $err = 1 ]
    then
        output_error "GIT: Please commit or stash them."
        exit 1
    fi

    output_success "GIT" "work tree is clean"
}

git_require_remote_branch_head_equals_local_branch_head() {
  REMOTE_REV=$(git rev-parse origin/main)
  LOCAL_REV=$(git rev-parse HEAD)

  if [ "$REMOTE_REV" != "$LOCAL_REV" ]
  then
    output_error "GIT" "remote or local branch revision not up to date.\n\tPlease push your changes to origin/main"
    exit 1
  fi

  output_success "GIT" "remote and local branch HEAD up to date"
}

get_bumped_version() {
  CURRENT_VERSION=$1
  RELEASE_TYPE=$2

  MAJOR="$(cut -d '.' -f1 <<<"${CURRENT_VERSION}")"
  MINOR="$(cut -d '.' -f2 <<<"${CURRENT_VERSION}")"
  PATCH="$(cut -d '-' -f1 <<<"$(cut -d '.' -f3 <<<"${CURRENT_VERSION}")")"

  case "$RELEASE_TYPE" in
    "major")
      MAJOR=$((MAJOR+1))
      MINOR=0
      PATCH=0
      ;;
    "minor")
      MINOR=$((MINOR+1))
      PATCH=0
      ;;
    "patch")
      PATCH=$((PATCH+1))
      ;;
    *)
      output_error "Input '${RELEASE_TYPE}' did not match any of: 'major', 'minor', 'patch'"
      exit 1
      ;;
  esac

  output_success "VERSION" "${MAJOR}.${MINOR}.${PATCH}"
}

git_commit_version_change() {
  RELEASE_VERSION=$1
  git commit -am "Prepare release (${RELEASE_VERSION})"
  git tag "${RELEASE_VERSION}"
  git push origin main --follow-tags
}

git_commit_snapshot_change() {
  RELEASE_VERSION=$1
  git commit -am "Set snapshot version (${RELEASE_VERSION}-SNAPSHOT)"
}

# HAPI FHIR testcontainer too unstable, so recomended to run tests manually before deploying.
#maven_verify_the_project() {
#  mvn -U clean verify
#  output_success "MAVEN" "Verify succeeded"
#}

ask_which_release_type_then_bump_version_and_deploy() {
  CURRENT_VERSION="$(
   echo "$(mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version)"\
    | grep -v "\["\
    | grep -v "Downloading"\
  )"

  output_awating_action "MAVEN" "Type in the type of release:
    'major' - version when you make incompatible API changes
    'minor' - when you add functionality in a backwards compatible manner
    'patch' - version when you make backwards compatible bug fixes"
  read -r RELEASE_TYPE

  NEW_VERSION=$(get_bumped_version "$CURRENT_VERSION" "$RELEASE_TYPE")
  mvn versions:set -DgenerateBackupPoms=false -DnewVersion="${NEW_VERSION}"

  output_success "MAVEN" "Bumped pom.xml version to: ${NEW_VERSION}"

  git_commit_version_change "${NEW_VERSION}"

  output_success "GIT" "Committed new version: ${NEW_VERSION}"

  mvn -U clean deploy -DskipTests=true

  output_success "MAVEN" "Deployed new artifact version: ${NEW_VERSION}"

  mvn versions:set -DgenerateBackupPoms=false -DnewVersion="${NEW_VERSION}-SNAPSHOT"
  git_commit_snapshot_change "${NEW_VERSION}"
}

git_require_on_main_branch
git_require_clean_work_tree
git_require_remote_branch_head_equals_local_branch_head
#maven_verify_the_project
ask_which_release_type_then_bump_version_and_deploy

