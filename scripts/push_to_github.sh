#!/usr/bin/env bash
set -euo pipefail

REPO_URL="${1:-}"
if [[ -z "$REPO_URL" ]]; then
  echo "Usage: scripts/push_to_github.sh https://github.com/<user>/<repo>.git"
  exit 1
fi

if [ ! -d ".git" ]; then
  git init
fi

git add .
git commit -m "Local Music Assistant: initial push with Vosk + Media3 + wake word + CI" || true
git branch -M main

git remote remove origin 2>/dev/null || true
git remote add origin "$REPO_URL"

git push -u origin main
echo "Pushed to $REPO_URL"
echo "Next: visit the Actions tab to download the APK artifact."