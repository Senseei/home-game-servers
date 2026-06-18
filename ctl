#!/usr/bin/env bash
# Convenience launcher so you can run `./ctl` (== ./scripts/ctl.sh, with no args = menu).
exec "$(dirname "$(readlink -f "$0")")/scripts/ctl.sh" "$@"
