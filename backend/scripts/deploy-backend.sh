#!/usr/bin/env bash
# F23: Blue-green backend deploy script.
#
# Pre-requisite: the target branch must already be merged to master on GitHub
# (origin) and the server must have the repo cloned at SERVER_DIR with
# git@github.com-careerloop deploy key configured.
#
# Usage:
#   ./scripts/deploy-backend.sh
#   DEPLOY_BRANCH=my-branch ./scripts/deploy-backend.sh   # deploy a specific branch
#
# What it does:
#   1. Pulls latest code from the deploy branch on the server
#   2. Tags the current image as :rollback (one-version safety net)
#   3. Rebuilds the Docker image from source (multi-stage Dockerfile)
#   4. Brings the backend container up; waits for health check
#   5. Auto-rolls back if health check fails within 90s
set -euo pipefail

SERVER_USER="${DEPLOY_USER:-ubuntu}"
SERVER_HOST="${DEPLOY_HOST:-43.138.240.228}"
SERVER_DIR="${DEPLOY_DIR:-/home/ubuntu/careerloop}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"

echo "========================================"
echo " CareerLoop Backend Deploy"
echo " Branch:  ${DEPLOY_BRANCH}"
echo " Target:  ${SERVER_USER}@${SERVER_HOST}:${SERVER_DIR}"
echo "========================================"

echo ""
echo "[1/5] Pulling latest code on server (branch: ${DEPLOY_BRANCH})..."
ssh "${SERVER_USER}@${SERVER_HOST}" "
  cd ${SERVER_DIR}
  git fetch origin
  git checkout ${DEPLOY_BRANCH}
  git pull origin ${DEPLOY_BRANCH}
  echo '  Code updated'
"

echo "[2/5] Tagging current image as rollback..."
ssh "${SERVER_USER}@${SERVER_HOST}" "
  docker tag careerloop-backend:latest careerloop-backend:rollback 2>/dev/null \
    && echo '  Tagged :latest → :rollback' \
    || echo '  No existing image to tag (first deploy)'
"

echo "[3/5] Rebuilding Docker image from source..."
ssh "${SERVER_USER}@${SERVER_HOST}" "
  cd ${SERVER_DIR}/backend
  docker compose --env-file .env.prod config --quiet
  docker compose --env-file .env.prod build app
"

echo "[4/5] Deploying new container..."
ssh "${SERVER_USER}@${SERVER_HOST}" "
  cd ${SERVER_DIR}/backend
  docker compose --env-file .env.prod up -d --no-deps app
"

echo "[5/5] Waiting for health check (up to 90s)..."
HEALTHY=false
for i in $(seq 1 18); do
  sleep 5
  STATUS=$(ssh "${SERVER_USER}@${SERVER_HOST}" "
    docker inspect --format='{{.State.Health.Status}}' careerloop-backend 2>/dev/null || echo 'starting'
  ")
  echo "  [${i}] Health: ${STATUS}"
  if [ "${STATUS}" = "healthy" ]; then
    HEALTHY=true
    break
  fi
done

if [ "${HEALTHY}" = "true" ]; then
  echo ""
  echo "✅ Deploy successful! Backend is healthy."
else
  echo ""
  echo "❌ Health check failed — auto-rolling back..."
  ssh "${SERVER_USER}@${SERVER_HOST}" "
    cd ${SERVER_DIR}/backend
    docker tag careerloop-backend:rollback careerloop-backend:latest 2>/dev/null || true
    docker compose --env-file .env.prod up -d --no-deps --force-recreate app
    echo 'Rollback applied'
  "
  echo "🔄 Rollback complete. Check logs:"
  echo "   ssh ${SERVER_USER}@${SERVER_HOST} 'docker logs careerloop-backend --tail 100'"
  exit 1
fi
