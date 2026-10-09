# Modular Pressure Tube Reactor (MPTR)

This is a WIP small personal-project Minecraft mod meant to be deployed alongside GregTech: New Horizons.  This mod is not part of the GTNH project at all, and since it's currently 100% slopcoded, and there are already two open projects by actual GTNH developers to create new nuclear reactor machines for the pack ([RecursivePineapple's Nuclear Horizons](https://github.com/RecursivePineapple/NuclearHorizons) as a direct IC2 nuke replacement, and [@datlycan's AoI design document](https://docs.google.com/document/d/1gSaYIHG5dJFg5WUhulF6j5pab75Lc-buJcVvA96T6ps)), this mod will most likely never be part of GTNH.  The only part of this repository right now that actually comes from my head is this README.

That said: this is an adaptation of the Modern Industrialization nuclear reactor, because I really enjoyed playing that machine back when I played ATM10, and I was nostalgic for it.

To be clear: I haven't looked at a single line of code in this app; it all comes from Antigravity.  As a longtime software engineer, I have complicated / weird feelings about this.  But anyway, that whole can of worms aside, here it is.  Play with it, it's fun.  If you're interested, hit me up (mgomezch in GTNH Discord).

It works as an add-on to GTNH 2.9 (tested on some daily shortly after release candidate 1).  It's unfinished, and completely unbalanced, but it's kinda fun to mess with anyway.  It does require a small change to GTNH GT5 since I added Compressor recipes that produce fluids, but otherwise it's stand-alone.

License: GPL3, because I originally started the project as a bunch of modifications to GTNH's GT5-Unofficial repo, and this app has stuff extracted from that project, so its license holds.  See https://github.com/GTNewHorizons/GT5-Unofficial/blob/master/LICENSE.txt

There's also a simulator as a stand-alone client-only Web app hosted on GitHub Pages:
* Production: https://mgomezch.github.io/ModularNuclear/
* Staging: https://mgomezch.github.io/ModularNuclear/staging/

---

## Build

```bash
./gradlew build

# Build the WebAssembly standalone simulator
./gradlew generateWasm exportStaticDist
```

---

## License

GPLv3. See [LICENSE](https://github.com/GTNewHorizons/GT5-Unofficial/blob/master/LICENSE.txt) for details.
