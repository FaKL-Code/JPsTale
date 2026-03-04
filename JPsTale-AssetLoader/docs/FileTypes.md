# File Type Reference

## Equipment

### Models

    `image/Sinimage/Items/DropItem/it{0}.smd`.format(item.dropItem);


### Icons

    `image/Sinimage/Items/{0}/it{1}.bmp`.format(item.folder, item.catetory);

### Scripts

Text files.

These files record equipment name, code, price, attributes, requirements, and other parameters.

    `GameServer/OpenItem/{0}.txt`.format(item.catetory);

Example — `OR133.txt`:

    *Name "Ring of Apollo"
    *Code "OR133"
    *Weight 7
    *Price 120000
    *StaminaRegen 63 63
    *HPIncrease 150 155
    *MPIncrease 150 150
    *StaminaIncrease 1500 1500
    *Level 150
    *Spirit 140
    **RandomEffect Knight Atalanta Mechanician Fighter Pikeman Archer Assassin Shaman Priestess Magician
    **HPRegen 2.9 2.9

## Characters

Players, monsters, and NPCs are all characters. Monsters are a special type of NPC. NPCs can talk to players and trade items; monsters will attempt to attack hostile characters within their vision range.

### Models

**in, ini, inf**

Text files.

These three file types share the same script format, used to associate a character's "appearance file" with its "animation file." After being read by the program, they generate `.inx`, `.smb`, and `.smd` files.

Some models do not use their own animations and may share animation data with other models.

Example 1 (`flag.inf`):

    // 캐릭터 주인공 설정값
    *모양파일	wow.ase
    //*정밀모양	"MmhD03c_h"
    //*보통모양	"MmhD03c_h"
    //*저질모양	"MmhD03c_h"
    //*파일연결	M3bip.in

Monster skeletal animations are typically stored in a single complete ASE file. The `.ini` script records how to decompose these animations.

Example 2 (`char/monster/death_knight/death_knight.ini`):

    // 캐릭터 주인공 설정값
    // Model File
    *모양파일	death_knight.ASE

    // High Model
    *정밀모양	"death knight"
    // Default Model
    //*보통모양	"death knight"

    // Motion File
    *동작파일	death_knight.ASE

    *동작모음	"death_knight.ASE"

    // Animations
    //*Idle 10 70 Loop
    *서있기동작		10		70	반복
    //*Walk 80 116 Loop
    *걷는동작		80		116	반복
    //*Run 120 144 Loop
    *뛰는동작		120		144	반복
    // Right to left swing
    //*Attack 320 364 340
    *공격동작		320		364	340
    // Left to right swing
    //*Attack1 380 425 405
    *공격동작1		380		425	405
    //*Attack2 440 485 467
    *공격동작2		440		485	467
    //*Attack3 250 310 268 285 P
    *공격동작3		250		310	268	285	P
    //*Attack4 570 630 589 608 L
    *공격동작4		570		630	589	608	L
    //*HitReaction 150 173 K
    *타격동작		150		173	K
    //*Skill 500 560 541 N
    *기술동작		500		560	541	N
    //*Death 180 238 A
    *죽기동작		180		238	A

    // End
    *끝

NPC models are similar to monsters.

Example 3:

`char/npc/bcn01/Bcn01.ini`

    // 캐릭터 주인공 설정값

    *모양파일	Bcn01.ASE

    *정밀모양	"Bcn01_h"
    *보통모양	"Bcn01_m"

    *동작파일	Bcn01.ASE

    *동작모음	"Bcn01.ASE"

    *걷는동작		140		176

    *서있기동작		10		130	E

    *표정파일연결	Bcn01f.in

    *끝

`char/npc/bcn01/Bcn01f.in`

    // NPC 표정 파일 설정값.

    *표정파일	Bcn01f.ASE

    // 말하기
    *표정모음	"Bcn01f.ASE"

    *눈깜빡표정		10		16	(40)
    *아표정 		30		45	(30)
    *오표정			50		67	(30)

    // 표정
    *표정모음	"Bcn01f.ASE"

    *화난표정		80		130		(50)
    *무표정(작동않함)	1		30	P	(50)

    *끝

Example 4 (`char/tmABCD/CtmbC92.ini`):

    // Model File
    *모양파일		CtmbC92.ASE
    // Link File
    *파일연결		M4Bip.in
    // End
    *끝

The script above links to `M4Bip.in`, but the program should actually read the `M4Bip.inx` file instead.

**inx**

Binary file.

This file records where the character model and animations are stored. Most importantly, it records how to decompose the full skeletal animation into individual animation clips.

**smb**

Binary file.

This file stores the character's skeleton and skeletal animation data.

**smd**

Binary file.

This file stores the character's 3D mesh data.

### Scripts

**npc**

These scripts describe NPCs and are only stored on the server side, at `GameServer/NPC/*.npc`.

Example 1: Navisko Death Match NPC

    *Type NPC
    *ModelFile "char\npc\SN-002\SN-002.ini"
    *HP 100
    *Name "Water Spirit Ariel"
    *Dialogue "May the power, wisdom, and beauty of water be with you"
    *Dialogue "The Trial Tower is operated by 'XXX'"
    *Dialogue "You're late, a match is in progress"
    *Dialogue "May the power, wisdom, and beauty of water be with you"
    *Dialogue "You look strong too"
    *Dialogue "You cannot enter right now"
    *Dialogue "Daily earnings will be used for arena maintenance"
    *Dialogue "A fee is required to enter the arena"
    *Dialogue "May the power, wisdom, and beauty of water be with you"
    *EventAccept 2

Example 2: Ricarten Blacksmith NPC

    *Type NPC
    *ModelFile "char\npc\TN-004\TN-004.ini"
    *Name "Blacksmith Ruga"
    *Dialogue "What do you need?"
    *Rank 1021
    *HP 100
    *WeaponSale WA107 WA108 WA109 WC107 WC108 WC109 WH108 WH109 WH110	WP108 WP109 WP110	WS109 WS110 WS111	WS209 WS210 WS211
    *ArmorSale DA108 DA109 DA110 DB107 DB108 DB109 DG107 DG108 DG109	OA207 OA208 OA209	DS107 DS108 DS109
    *ItemSale None

**inf**

This file type records character information, typically for monster scripts. Stored on the server at `GameServer/Monster/*.inf`.

    *Type			Monster
    *ModelFile		"char\monster\Monclip\monclip.ini"
    *DisplayOffset		0	14
    *Name			"Imp"
    *Level			38
    // Monster activity time
    *ActivityTime		Unlimited
    // Organization: min count - max count
    *Organization		1 2
    // IQ on a scale of 1-10
    *Intelligence		9
    // Temperament: Good, Neutral, Evil
    *Temperament		Evil
    // Vision range (1M = 27)
    *VisionRange		360
    // HP
    *HP			420
    // Attack power
    *Attack			26 44
    // Damage absorption rate (%)
    *Absorption		6
    // Block rate
    *BlockRate		0
    // Defense
    *Defense		200
    // Attack speed (range: 5-9)
    *AttackSpeed		8
    // Accuracy
    *Accuracy		500
    // Special attack chance (min damage / max damage)
    *SpecialAttackRate	20
    // Body size: Small, Medium, Medium-Large, Large, None
    *Size			Medium
    // Attack range
    *AttackRange		50
    // Elemental resistances
    ////////// Elemental Values //////////
    *MagicRes		15
    *LightningRes		0
    *IceRes			50
    *FireRes		50
    *PoisonRes		50
    *Flow			-4
    // Monster race
    *MonsterRace		Demon
    // Movement speed (1-6)
    *MoveSpeed		6
    // Movement type
    *MoveType		0
    // Potion reserves (number of potions the monster carries)
    *PotionReserve		0
    // Potion use threshold
    *PotionRetainRate	0
    // Sound effect ( CYCLOPS / HOBGOBLIN / IMP / MINIG / PLANT / SKELETON / ZOMBI / OBIT )
    *SoundEffect		CRIP
    // Experience points
    *Experience		4300
    // Item drop configuration
    *ItemDropCount		1
    *Item		4400	None
    *Item		3500	Gold	40 100
    *Item		1000	pl102 pl103 ps103 pl102 pm102
    *Item		700	da107 da207 wm106 wn106 ws106 wd106 ws206 wt105 pm102 ds105 or107 oa107 ec102 GP105 GP106 GP107 GP108 GP111
    *Item		300	da108 da208 wm107 wn107 ws107 wd107 ws207 wt106 db106 ds106 dg106 oa206 ec102 or107 oa107
    *Item		100	da109 da208 wm108 wn108 wa108 wd108 ws208 wt107 db107 ds107 dg107 oa207 ec102 os105 or108 oa108
    *LinkFile	"name\38_Crypt.zhoon"

## Regions (Field)

### Models

The main map model is stored in an `.smd` file.

Each map also has supplementary objects stored in `.smd` files. Some stage objects have animations, which appear to also be stored in `.smd` files.

### Scripts

**spm**

Text file.

This file stores the spawn rules for each map, located at `GameServer/Field/*.ase.spm` on the server.

Rules include the maximum monster count, monster types, spawn frequency, spawn probability per type, boss spawn times, etc.

    *TotalMonsters 200
    *SpawnInterval 5 14
    *GroupSize 3
    *MonsterType "Mushroom Spirit" 12
    *MonsterType "Beast Soldier" 35
    *MonsterType "Fire Sprite" 10
    *MonsterType "Green Treant" 20
    *MonsterType "One-Eyed Demon" 3
    *MonsterType "Biped Insect" 20
    *BossType "Super Treant" "Zombie" 8 14 18 21 23 03

**spp**

Binary file.

This file records the spawn point positions within each map. Each map has at most 200 spawn points. Each point is stored as 3 integers (12 bytes total):

    struct STG_START_POINT {
        int state; // Whether this point is active: 0 = unused, 1 = in use.
        int x,z;   // Spawn point coordinates on the world map.
    };

Since the number of spawn points and the data structure are fixed, every `.spp` file has a fixed size of 2,400 bytes.

**spc**

Binary file.

This file stores NPC information within a map, including each NPC's position and facing direction. The `CharInfo` section stores the NPC's detailed attributes.

A map can hold at most 100 NPCs. Each NPC occupies 504 bytes, so every `.spc` file has a fixed size of 50,400 bytes.
