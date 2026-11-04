#!/usr/bin/env bash
# Starts the mock vendor (8081), the BFF (8080) and the web UI (5173).
# Press Ctrl+C to stop all three.
set -euo pipefail
cd "$(dirname "$0")"

# When the script stops, stop everything it started, then wait for it to finish
# so the prompt only comes back once all three services have shut down.
# `kill 0` signals every process in this script's process group; the script
# ignores that signal itself so it can stay around and wait.
stop_all() {
  trap '' INT TERM
  trap - EXIT
  kill 0
  wait
}
trap stop_all INT TERM EXIT

# Install web dependencies on the first run.
[ -d web/node_modules ] || (cd web && npm install)

# Start each service in the background, loading its .env file if there is one.
# `exec` replaces the subshell with the service, so `wait` waits for the service.
(cd mock-vendor && if [ -f .env ]; then set -a; . ./.env; fi; exec ./mvnw -q spring-boot:run) &
(cd bff && if [ -f .env ]; then set -a; . ./.env; fi; exec ./mvnw -q spring-boot:run) &
(cd web && exec npm run dev) &

echo "Mock vendor: http://localhost:8081"
echo "BFF:         http://localhost:8080"
echo "Web UI:      http://localhost:5173"

# Keep running until Ctrl+C.
wait
