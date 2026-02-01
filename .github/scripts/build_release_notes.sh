#!/usr/bin/env bash
set -euo pipefail
# build_release_notes.sh
# Generates release notes combining annotated tag message and CHANGELOG.md section.
# Usage: build_release_notes.sh <tag> <repo_owner> <repo_name> <commit_sha> <github_server_url>
# Outputs markdown to STDOUT.

TAG=${1:?tag required}
REPO_OWNER=${2:?owner required}
REPO_NAME=${3:?name required}
TAG_COMMIT=${4:?commit sha required}
GITHUB_SERVER_URL=${5:-https://github.com}

# Normalize versions (strip leading v)
# VERSION_FULL keeps suffixes like -rc1, VERSION_CORE strips them
VERSION_FULL=$(echo "$TAG" | sed -E 's/^v//')
VERSION_CORE=$(echo "$VERSION_FULL" | sed -E 's/^([0-9]+(\.[0-9]+)*).*/\1/')

# Get tag message. Try annotated tag contents first, then fall back to the tagged commit message
# but only if the tag actually exists. If the tag name doesn't resolve to a tag ref, do nothing.
TAG_MESSAGE=""
# Check whether the tag ref exists
if git rev-parse --verify --quiet "refs/tags/${TAG}" >/dev/null 2>&1; then
  # Try annotated tag contents (works for annotated tags)
  TAG_MESSAGE=$(git for-each-ref --format='%(contents)' "refs/tags/${TAG}" 2>/dev/null || true)

  # If empty, try to read tag object (cat-file) - may include annotation
  if [ -z "${TAG_MESSAGE}" ]; then
    TAG_MESSAGE=$(git cat-file -p "refs/tags/${TAG}" 2>/dev/null | sed -n '1,/^$/p' || true)
  fi

  # If still empty, fall back to commit message of the tagged commit (works for lightweight tags)
  if [ -z "${TAG_MESSAGE}" ]; then
    if git rev-parse "${TAG}^{commit}" >/dev/null 2>&1; then
      TAG_MESSAGE=$(git show -s --format='%B' "${TAG}^{commit}" 2>/dev/null || true)
    else
      TAG_MESSAGE=$(git show -s --format='%B' "${TAG}" 2>/dev/null || true)
    fi
  fi
else
  # Tag reference does not exist; leave tag message empty
  TAG_MESSAGE=""
fi

# Remove potential trailing whitespace in tag message
TAG_MESSAGE=$(printf '%s' "${TAG_MESSAGE}" | sed -E ':a; /\n$/ {N; ba}; s/[[:space:]]+$//')
# Remove potential trailing whitespace in tag message
TAG_MESSAGE=$(printf '%s' "${TAG_MESSAGE}" | sed -E ':a; /\n$/ {N; ba}; s/[[:space:]]+$//')

# Extract the section from CHANGELOG.md belonging to this version
CHANGELOG_CONTENT=""
if git show "${TAG_COMMIT}:CHANGELOG.md" > /tmp/CHANGELOG_ALL 2>/dev/null; then
  # Grab section starting after heading line matching version until next heading
  # Try full version first (e.g., 1.0.0-rc1), then fall back to core (e.g., 1.0.0)
  EXTRACTED=""
  for try_ver in "${VERSION_FULL}" "${VERSION_CORE}"; do
    EXTRACTED=$(awk -v ver="${try_ver}" '
      BEGIN{found=0}
      $0 ~ "^##[[:space:]]*\\[" ver "\\]" {found=1; next}
      found && $0 ~ "^##[[:space:]]*\\[" {exit}
      found {print}
    ' /tmp/CHANGELOG_ALL)
    if [ -n "${EXTRACTED}" ]; then
      break
    fi
  done
  if [ -n "${EXTRACTED}" ]; then
    CHANGELOG_CONTENT="${EXTRACTED}"
  fi
fi

# Filter to sections Added / Changed / Fixed / Security, keep their content blocks until next heading of same depth (### )
if [ -n "${CHANGELOG_CONTENT}" ]; then
  FILTERED=$(awk '
    BEGIN{cur=""; keep=0}
    /^###[[:space:]]+Added/ {cur="Added"; keep=1; print; next}
    /^###[[:space:]]+Changed/ {cur="Changed"; keep=1; print; next}
    /^###[[:space:]]+Fixed/ {cur="Fixed"; keep=1; print; next}
    /^###[[:space:]]+Security/ {cur="Security"; keep=1; print; next}
    /^###/ {cur=""; keep=0}
    {if(keep){print}}
  ' <<<"${CHANGELOG_CONTENT}")
  # If filtering yielded something non-empty use it, else keep original
  if [ -n "${FILTERED}" ]; then
    CHANGELOG_CONTENT="${FILTERED}"
  fi
fi

# Trim leading/trailing blank lines
trim_blank() { sed -E '/^[[:space:]]*$/{$d;}; 1{/^[[:space:]]*$/d;}' ; }
CHANGELOG_CONTENT=$(printf '%s\n' "${CHANGELOG_CONTENT}" | sed -E ':a;/^$/{$d;N;ba};' | sed -E '1{/^$/d;}') || true

# Build compare link (try to determine a previous tag)
COMPARE_LINK=""

# Try several ways to find a previous tag: by creation date, then by semantic/version sort,
# then by using git describe on the parent commit. This improves chances of producing a
# meaningful compare URL on repositories with different tag styles.
PREV_TAG=""
PREV_TAG=$(git tag --sort=-creatordate | grep -Fvx "${TAG}" | head -n1 || true)
if [ -z "${PREV_TAG}" ]; then
  PREV_TAG=$(git tag --sort=-v:refname | grep -Fvx "${TAG}" | head -n1 || true)
fi
if [ -z "${PREV_TAG}" ]; then
  PREV_TAG=$(git describe --tags --abbrev=0 "${TAG}^" 2>/dev/null || true)
fi
if [ -n "${PREV_TAG}" ]; then
  # Only build a compare URL if the requested TAG actually resolves to something the
  # git server can compare to: either a tag ref or a commit-ish.
  if git rev-parse --verify --quiet "refs/tags/${TAG}" >/dev/null 2>&1 || git rev-parse --verify --quiet "${TAG}" >/dev/null 2>&1; then
    COMPARE_URL="${GITHUB_SERVER_URL}/${REPO_OWNER}/${REPO_NAME}/compare/${PREV_TAG}...${TAG}"
    COMPARE_LINK="Full changelog: ${COMPARE_URL}"
  fi
fi

# Assemble final notes without embedding literal \n sequences
segments=()
if [ -n "${TAG_MESSAGE}" ]; then
  segments+=("${TAG_MESSAGE}")
fi
if [ -n "${CHANGELOG_CONTENT}" ]; then
  segments+=("${CHANGELOG_CONTENT}")
fi
if [ -n "${COMPARE_LINK}" ]; then
  segments+=("${COMPARE_LINK}")
fi

# Join segments with a blank line
if [ ${#segments[@]} -gt 0 ]; then
  # Print each segment separated by a blank line
  {
    for i in "${!segments[@]}"; do
      printf '%s' "${segments[$i]}"
      if [ "$i" -lt $((${#segments[@]} - 1)) ]; then
        printf '\n\n'
      else
        printf '\n'
      fi
    done
  } | sed -E ':a;/^$/{$d;N;ba};' # trim trailing blank lines
fi
