# JPsTale — Priston Tale Map Texture Customization Tool

A set of tools for loading, inspecting, and customizing map textures from the 3D MMORPG "Priston Tale", built in Java with the **jMonkeyEngine** 3D engine.

This project is divided into several subprojects that are developed in parallel.

## AssetLoader

A jME3 plugin for parsing Priston Tale asset files.

**AseLoader**

- Parses mesh, skeleton, and animation data from `.ase` model files.

**InxLoader**

- Parses animation index data from `.inx` format files.
- Parses 3D mesh data from `.smd` format files.
- Parses skeletal animation data from `.smb` format files.

## Resource Parsing

**Assets**

- Parses various Priston Tale resource files, including: maps, textures, models, audio, effects, and sky.

**DataEntity**

- Defines the data structures used by Priston Tale assets.

## Main Tool

**Field (FieldBox)**

- The primary map texture customization and visualization tool.
- Load and render Priston Tale maps in 3D with their original textures.
- Inspect and identify textures applied to any surface via the texture picker.
- Visual wireframe highlight overlay for all surfaces sharing a selected texture.
- Day/night cycle timeline with lighting preview.
- Skybox preview with real sky textures.
- Camera bookmark system with JSON persistence.
- HUD toggle (F1) and keyboard shortcut help panel (H).

## Auxiliary Tools

**Aging** — Forging simulator for adjusting success rates.

**Craft** — Crafting recipe manager.

**Monsters** — Monster attribute editor and comparison reports.

**NPC** — NPC manager (positions, item sale lists).

**Loots** — Monster drop table editor with drop-rate statistics.

## GUI

**GUI**

- Recreates the game's main interface using Lemur + Groovy scripts.
