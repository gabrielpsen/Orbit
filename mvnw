#!/usr/bin/env sh
set -eu
MVN_VERSION="3.10.0"
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if command -v mvn >/dev/null 2>&1; then
  VERSION="$(mvn -version 2>/dev/null | sed -n '1s/Apache Maven //p')"
  case "$VERSION" in 3.10.0) exec mvn "$@" ;; esac
fi
HOME_DIR="${MAVEN_USER_HOME:-${HOME}/.m2}"
CACHE_DIR="${HOME_DIR}/wrapper/dists/apache-maven-${MVN_VERSION}"
MVN_BIN="${CACHE_DIR}/apache-maven-${MVN_VERSION}/bin/mvn"
if [ ! -x "$MVN_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  ARCHIVE="${CACHE_DIR}/apache-maven-${MVN_VERSION}-bin.tar.gz"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MVN_VERSION}/apache-maven-${MVN_VERSION}-bin.tar.gz"
  CHECKSUM_URL="${URL}.sha512"
  echo "Downloading Apache Maven ${MVN_VERSION} and verifying SHA-512..."
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$URL" --output "$ARCHIVE"
    CHECKSUM="$(curl --fail --location --silent --show-error "$CHECKSUM_URL" | awk '{print $1}')"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$ARCHIVE"
    CHECKSUM="$(wget -qO- "$CHECKSUM_URL" | awk '{print $1}')"
  else
    echo "Install curl or wget to bootstrap Maven" >&2; exit 1
  fi
  case "$CHECKSUM" in
    *[!0-9a-fA-F]*|'') echo "Invalid Apache checksum response" >&2; rm -f "$ARCHIVE"; exit 1 ;;
  esac
  [ "${#CHECKSUM}" -eq 128 ] || { echo "Unexpected SHA-512 length" >&2; rm -f "$ARCHIVE"; exit 1; }
  if command -v sha512sum >/dev/null 2>&1; then
    printf '%s  %s\n' "$CHECKSUM" "$ARCHIVE" | sha512sum --check --status
  elif command -v shasum >/dev/null 2>&1; then
    printf '%s  %s\n' "$CHECKSUM" "$ARCHIVE" | shasum -a 512 --check --status
  else
    echo "Install sha512sum or shasum to verify Maven" >&2; rm -f "$ARCHIVE"; exit 1
  fi
  tar -xzf "$ARCHIVE" -C "$CACHE_DIR"
  rm -f "$ARCHIVE"
fi
exec "$MVN_BIN" -f "$BASE_DIR/pom.xml" "$@"
