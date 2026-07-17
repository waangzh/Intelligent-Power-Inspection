#!/usr/bin/env sh
set -eu

usage() {
  echo "usage: $0 --check|--write <robot-repo-root>" >&2
  exit 2
}

[ "$#" -eq 2 ] || usage
mode=$1
robot_root=$2
platform_file=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)/docs/protocol/robot-platform-v1.md
robot_file=$robot_root/docs/protocol/robot-platform-v1.md

[ -d "$robot_root" ] || { echo "robot repository not found: $robot_root" >&2; exit 2; }

case "$mode" in
  --check)
    cmp "$platform_file" "$robot_file"
    ;;
  --write)
    mkdir -p "$(dirname -- "$robot_file")"
    cp "$platform_file" "$robot_file"
    ;;
  *) usage ;;
esac

sha256sum "$platform_file" "$robot_file"
