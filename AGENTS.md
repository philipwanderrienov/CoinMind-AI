# CoinMind-AI Agent Instructions

These instructions apply to ChatGPT/Codex work on this repository.

## Before doing any work

1. Read `docs/development/current-state.md`.
2. Read the latest entries in `docs/development/session-log.md`.
3. Inspect the latest `main` branch before editing.
4. Preserve existing working behavior unless the task explicitly requires changing it.

## Shared synchronization rule

GitHub is the shared source of truth between the ChatGPT conversation and Codex running on the server.

For every meaningful code, architecture, deployment, or UX change:

1. update the implementation;
2. update `docs/development/current-state.md` so it reflects the new current state;
3. append a concise entry to `docs/development/session-log.md`;
4. commit the related changes together whenever practical;
5. push to `main` only after relevant checks/builds succeed.

Do not treat chat history as the canonical project state. The handoff files in this repository are canonical.

## Current priorities

- Mobile UX is currently the more polished view. Do not regress it while fixing desktop.
- Desktop dashboard composition and proportions are the main open UI priority.
- Technical/multi-timeframe analysis remains the primary decision source.
- AI News Intelligence and Polymarket are bounded supporting signals only.

## Project layout

- Backend: `backend/` — Java/Spring Boot
- Frontend: `frontend/` — Angular
- Production frontend root: `/var/www/coinmind/`
- Backend service: `coinmind-backend`
- Web server: Nginx

## Deployment commands

Frontend:

```bash
cd /home/phive/Documents/CoinMind-AI
git pull origin main
cd frontend
npm run build
sudo rm -rf /var/www/coinmind/*
sudo cp -r dist/coinmind-web/browser/* /var/www/coinmind/
sudo systemctl reload nginx
```

Backend, when backend code changes:

```bash
cd /home/phive/Documents/CoinMind-AI
git pull origin main
cd backend
mvn clean package -DskipTests
sudo systemctl restart coinmind-backend
sudo systemctl status coinmind-backend
```

## Validation expectations

- Frontend changes: run the Angular production build.
- Backend changes: run Maven package/tests appropriate to the change.
- UI changes: preserve responsive behavior and explicitly consider both mobile and desktop.
- Do not report a task complete if the relevant build/check has not passed.

## Server inspection

When Codex is used directly on `phive-server`, it may inspect local deployment state, logs, Nginx, services, and localhost as needed for the requested task.

Prefer read-only inspection first. Avoid destructive server changes unless the requested task clearly requires them.

## Handoff files

- `docs/development/current-state.md`: snapshot of current implementation, open issues, deployment/access state, and next priorities.
- `docs/development/session-log.md`: chronological log of meaningful changes and decisions.

Keep these concise enough that a new ChatGPT or Codex session can become productive quickly.
