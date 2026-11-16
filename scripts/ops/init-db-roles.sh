#!/bin/sh
set -eu
# Only initialize new empty volumes; existing installations execute configure-db-roles.sh explicitly.
exec /bin/sh /opt/blog-ops/configure-db-roles.sh
