# Visor 0.4.0 — Sable Compatibility Fix — Unofficial NeoForge 1.21.1 Release

This is an unofficial NeoForge 1.21.1 build of Visor https://github.com/VisorModStudio/Visor. It is not an official release from the Visor team.

I made this build because many players requested support for NeoForge 1.21.1 and because I wanted to play this version myself. I got it working on a Meta Quest 3 using Virtual Desktop with VDXR, as well as through SteamVR. It also worked with All The Mons, a modpack containing nearly 400 mods.

## Important disclaimer

This build may be heavily unstable for some users. Multiplayer has not been tested, other headsets and configurations are unconfirmed, and your mileage may vary.

## Sable compatibility

Compatibility with Sable and Create Aeronautics has been fixed. Visor's leash-position and name-tag camera hooks now compose with Sable instead of competing for the same mixin redirects.

The release is identified by name as the Sable compatibility build, while the internal mod version remains `0.4.0`. This keeps existing Visor addons that depend on Visor `0.4.0` compatible.

The source still contains dead or unused code. I intentionally left it in place because the official release appears not far away, and this build is meant to give players access in the meantime.

If you encounter an issue, please report it to Joulias through Discord or open an issue in this GitHub repository. Your reports may also provide useful information for the official project's continuing development.