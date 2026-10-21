# Changelog

## 1.0.0 (2026-10-16)

First version, for Minecraft 1.20.1, Forge 47 and The eYes Remastered 1.3.0.

- Faster: each eYe's target search only checks line of sight to the entities that could change its target, and skips the search when it's already after a player.
- Faster: third-stage satellites and trees only give their effects again when a mob's copy is close to running out, so Forge's effect events (and every mod listening to them) run about a quarter as often.
- Faster: a satellite searches its surroundings once a tick instead of twice, without sorting.
- Faster: eYe Soil and the other spreading blocks stop at once when no eYe tree is near, and build their block tags once.
- Logs one line per patched class at startup, saying how many changes applied.
- Works on the server, the client or both, and the two don't need to match.
