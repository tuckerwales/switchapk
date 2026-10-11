#!/bin/sh
# Runs tests/dex/TlsTest.java (reference JVM vs switchapk) against the local servers of tests/tls/server.py.
#   tests/tls/run_tls_test.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FIFO=$(mktemp -u)
mkfifo "$FIFO"
python3 "$ROOT/tests/tls/server.py" < "$FIFO" > "$FIFO.out" 2>&1 &
SERVER=$!
exec 3> "$FIFO"
for i in 1 2 3 4 5 6 7 8 9 10; do
    grep -q READY "$FIFO.out" 2>/dev/null && break
    sleep 0.5
done
"$ROOT/tests/run_dex_test.sh" "$ROOT/tests/dex/TlsTest.java"
STATUS=$?
exec 3>&-
wait $SERVER 2>/dev/null
rm -f "$FIFO" "$FIFO.out"
exit $STATUS
