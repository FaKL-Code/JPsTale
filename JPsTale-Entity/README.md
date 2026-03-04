# Data Entities

Defines the data structures used by Priston Tale assets. These entities are used by the map texture customization tool to interpret and manipulate game data. The overall conceptual model (work in progress) is shown below:

![Conceptual Model](cdm.png)

## Core Entities

- Level (level / experience points)
- Attack (attack power / attack speed / accuracy / critical rate / range / attack type / magic proficiency)
- Defense (absorption / defense value / block rate / evasion / stun resistance)
- Elemental Resistance (earth / fire / lightning / ice / poison)
- Movement Speed
- Recovery (HP / MP / stamina regeneration per second)
- Enhancement (directly increases HP / MP / stamina values)

## Player / Monster / NPC

Players, monsters, and NPCs are interactive objects in the game. They share some common entities.

### Appearance

- Position (coordinates / facing direction)
- Body type
- Model
- Sound effects

### Player

- Level (level / experience points)
- Attributes (strength / spirit / talent / agility / constitution)
- Class (ID / race)

### Monster

- Artificial Intelligence (temperament / IQ / vision range / attack behavior / defense behavior / healing behavior / flee behavior)
- Item drops
- Speech

### NPC

- Dialogue
- Items for sale
- Special functions (forging / crafting / item creation / socketing / rewards / warehouse / skill master / cash shop / prize exchange...)

## Region-Related Entities

- Map model
- Gateways
- Portals
- Respawn points
- Spawn points
- Spawning rules (quantity / type / frequency)
- Environment (sky / background music / ambient sound effects / interactive objects)

UPDATED by yan@2016/10/26
