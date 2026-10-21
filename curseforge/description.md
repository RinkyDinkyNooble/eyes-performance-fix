# eYes Performance Fix (Unofficial Patch)

A companion mod for The eYes Are Back 1.3.0 (Forge 1.20.1) that makes its mobs, satellites, trees and eYe Soil much cheaper for the server, with the same results. The eYes Are Back itself is still required.

## Before And After

A test server with about 100 eYes, a third-stage satellite and a third-stage tree, with the patch off and then on, 30 seconds each. With the eYes chasing a player: **20.1 ms** a tick without the patch, **7.4 to 9.7 ms** with it (most of what's left is the eYes finding their way to the player, which is ordinary mob AI). With no player around: **14.7 ms** without, **2.3 ms** with.

## Faster

- Every eYe searched for a target on every tick: it checked line of sight (a ray cast) to every entity within 60 blocks, other eYes included. Now it only checks the entities that could change its target, and none when it's already after a player. In the test, the search went from about 80 to 230 µs to under 10 µs per eYe and tick; in a 90-eYe fight it had been a third of the server's tick.
- Third-stage satellites and trees gave their effects to every mob around them on every tick, and each one went through Forge's effect events and every mod listening to them. Now an effect is only given again when the mob's own copy is close to running out, so it never runs out while the mob is in range. In the test, effect events went from about 520 to about 180 a tick. (A mob that walks out of a satellite's range can lose its boost up to a tenth of a second sooner.)
- A satellite searched the 125 blocks around it twice a tick, sorting everything by distance. Now once, unsorted.
- eYe Soil (and the eYes' other spreading blocks) searched for nearby eYe trees three times on every tick, sorting everything it found, and nearly always found none. Now it checks for each tree once and stops: about 6 µs down to under 1 µs per block tick.

Mobs target, move and spread exactly as before.

The log has one line for each patched class, saying how many changes applied.

Works on the server, the client or both, and the two don't need to match.

### Credit

This is an unofficial patch, not affiliated with The eYes Are Back. [The eYes Are Back](https://www.curseforge.com/minecraft/mc-mods/the-eyes) is made by Caliy the Calico and FabexSrednica.
