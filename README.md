# eYes Performance Fix (Unofficial Patch)

A companion mod for The eYes Are Back 1.3.0 (Forge 1.20.1) that makes its mobs, satellites, trees and eYe Soil much cheaper for the server, with the same results. The eYes Are Back itself is still required.

## Before And After

A big fight on Cinder, a planet in the ABZA modpack: about 90 eYes before and about 110 after, with a third-stage satellite and tree and one player.

- Without the patch the server ran at **16.4 TPS**, working 51 ms on each tick (the budget is 50).
- With it, a steady **20 TPS**, working 8 ms on each tick. Its slowest ticks (95th percentile) went from 168 ms to 9.5 ms.
- The eYes' own code went from 18.2 s of every 30 s of server time to 2.5 s.

In singleplayer it shows on screen too: **12 FPS** without the patch, about **130 FPS** with it (counter in the top left). The two screenshots aren't from exactly the same spot, since the eYes move around and keep spawning, but they're from the same fight.

Without:

![A big eYes fight without the patch, 12 FPS](https://raw.githubusercontent.com/RinkyDinkyNooble/eyes-performance-fix/main/curseforge/images/Before.png)

With:

![The same fight with the patch, about 130 FPS](https://raw.githubusercontent.com/RinkyDinkyNooble/eyes-performance-fix/main/curseforge/images/After.png)

## Faster

- Every eYe searched for a target on every tick: it checked line of sight (a ray cast) to every entity within 60 blocks, other eYes included. Now it only checks the entities that could change its target, and none when it's already after a player. In the fight: 8.1 s of every 30 s down to 0.46 s.
- Third-stage satellites and trees gave their effects to every mob around them on every tick, and each one went through Forge's effect events and every mod listening to them. Now an effect is only given again when the mob's own copy is close to running out, so it never runs out while the mob is in range. In the fight: the satellite 2.2 s down to 0.22 s, the tree 0.57 s down to 0.16 s. (A mob that walks out of a satellite's range can lose its boost up to a tenth of a second sooner.)
- A satellite searched the 125 blocks around it twice a tick, sorting everything by distance. Now once, unsorted.
- eYe Soil (and the eYes' other spreading blocks) searched for nearby eYe trees three times on every tick, sorting everything it found, and nearly always found none. Now it checks for each tree once and stops: 1.07 s down to 0.26 s.

Mobs target, move and spread exactly as before.

The log has one line for each patched class, saying how many changes applied.

Works on the server, the client or both, and the two don't need to match.

### Credit

This is an unofficial patch, not affiliated with The eYes Are Back. [The eYes Are Back](https://www.curseforge.com/minecraft/mc-mods/the-eyes) is made by Caliy the Calico and FabexSrednica.

## Building

Put `the_eyes_are_back-1.3.0-forge-1.20.1.jar` in `libs/` (it's gitignored), then run `gradlew build`. `gradlew verifyJar` builds a separate dev-only jar with `/eyesverify` commands that run The eYes' code with the patch off and on, on the same world, and compare the results; it is never shipped.

## License

0BSD, for this mod's own code. The icon is drawn by `curseforge/MakeIcon.java`. The eYes Are Back is not included.
