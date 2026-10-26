#!/usr/bin/env bash
# Builda e publica as 3 imagens no Docker Hub (e, opcionalmente, no registry local).
#   docker login                                  (uma vez)
#   DOCKERHUB_USER=meuuser TAG=1.0.0 ./scripts/docker-publish.sh
#   REGISTRY_LOCAL=1 ./scripts/docker-publish.sh  (também envia p/ localhost:5000)
set -euo pipefail
cd "$(dirname "$0")/.."
USER_HUB="${DOCKERHUB_USER:-nexonucleo5}"
TAG="${TAG:-latest}"

declare -A CTX=( [backend]=./backend [frontend]=./nexo [database]=./database )
for nome in backend frontend database; do
  img="$USER_HUB/nexo-$nome"
  echo ">> $img:$TAG"
  docker build -t "$img:$TAG" -t "$img:latest" "${CTX[$nome]}"
  docker push "$img:$TAG"
  [ "$TAG" = latest ] || docker push "$img:latest"
  if [ "${REGISTRY_LOCAL:-0}" = 1 ]; then
    docker tag "$img:$TAG" "localhost:5000/nexo-$nome:$TAG"
    docker push "localhost:5000/nexo-$nome:$TAG"
  fi
done
