# FieldBox — Map Texture Customization Tool

- The primary tool for visualizing and customizing Priston Tale map textures.
- Load and render maps in 3D with their original textures.
- Inspect and identify textures on any surface via the texture picker (middle-click / T).
- Wireframe highlight overlay for all surfaces sharing a selected texture.
- Add/remove world maps.
- Add/edit/remove portals.
- Edit/preview server-configured monster spawn points.
- Add/remove NPCs within a map.
- Day/night cycle timeline with lighting preview.
- Skybox preview with real sky textures.
- Camera bookmark system (JSON persistence).
- HUD toggle (F1) and keyboard shortcut help panel (H).

# Client-Side Files

Each region's data is stored across several files. The sections below describe their paths and purpose.

## Model Files

Model files are stored in the client's `Field/` directory and contain each map's 3D model. Inside `Field/`, the `map/` and `title/` subdirectories store the in-game minimaps and title images respectively. **These images are encrypted TGA files and must be decoded with the ImageDecoder tool.**

In theory, titles could be stored as plain text, but Priston Tale renders them as images. This way, when translating to different languages, you only need to replace the title images instead of modifying the program.

    String FieldDirectory = "Field/";       // Model files root directory
    String MapDirectory = "Field/map/";     // Minimap root directory
    String TitleDirectory = "Field/title/"; // Map title root directory

![Field directory](FieldDir.png)

Each region has a unique code in the game. The minimap path and title path can be generated from this code using a fixed pattern:

    Field/Map/%s.tga    // Minimap
    Field/Title/%st.tga // Title

Taking "Forest of Spirits" (commonly known as Forest 3) as an example, its server code is `"fore-3"`. Its model, minimap, and title image paths can be defined as follows:

    "Title":"Forest of Spirits",
    "Name":"fore-3"
    "Model":"Field/forest/fore-3.ASE",
    "Map":"Field/map/fore-3.tga",
    "Title":"Field/title/fore-3t.tga",

![forest-3](forest-3.png)

Each region has a main model containing the terrain, buildings, vegetation, and other objects. Additionally, some regions may contain standalone objects such as windmills and teleporters. These models may have simple animations like rotation or vertical floating. They are stored in the same directory as the main model.

Below are some supplementary objects in the `fore-3` region. The `Name` field indicates the model file path; the `Animation` field indicates whether the model has animation (1 = animated, 0 = static).

    "StageObject": [
    	{"Name":"forest/3ani-01.ASE", "Animation":0},
    	{"Name":"forest/3ani-02.ASE", "Animation":0},
    	{"Name":"forest/3ani-03.ASE", "Animation":0},
    	{"Name":"forest/3ani-04.ASE", "Animation":0},
    	{"Name":"forest/3ani-05.ASE", "Animation":0},
    	{"Name":"forest/3ani-06.ASE", "Animation":0},
    	{"Name":"forest/3ani-07.ASE", "Animation":0},
    	{"Name":"forest/3ani-08.ASE", "Animation":0},
    	{"Name":"forest/3ani-09.ASE", "Animation":0},
    	{"Name":"forest/3ani-10.ASE", "Animation":0},
    	{"Name":"forest/3ani-11.ASE", "Animation":0},
    	{"Name":"forest/3ani-12.ASE", "Animation":0},
    	{"Name":"forest/3ani-13.ASE", "Animation":0},
    	{"Name":"forest/3ani-14.ASE", "Animation":0}
    ],

## Audio Files

All of Priston Tale's audio files are in WAV format, stored in the `wav/` directory under the game root.

![wav directory](wav.png)

### Background Music (BGM)

Each scene has a corresponding background music track that loops continuously while the player is in that scene. BGM files are stored in the `BGM/` directory with a `.bgm` extension.

These files are actually WAV files with a renamed extension. Changing `.bgm` back to `.wav` allows playback in any media player.

![bgm.png](bgm.png)

Each BGM has an internal code, defined as follows:

    #define BGM_CODE_CUSTOM		-1
    #define BGM_CODE_TOWN1		1
    #define BGM_CODE_TOWN2		2
    #define BGM_CODE_VILLAGE	3
    #define BGM_CODE_FOREST		4
    #define BGM_CODE_DUNGEON	5
    #define BGM_CODE_FILAI		6
    #define BGM_CODE_SOD1		7
    #define BGM_CODE_SOD2		8
    #define BGM_CODE_SOD3		9
    #define BGM_CODE_DESERT		10
    #define BGM_CODE_ICE		11

In the Java implementation, enums are preferred for representing these. Also, WAV files are quite large, so converting them to OGG format is recommended.

    enum BGM {
    	CUSTOM(-1, "Custom", "bgm/Field - Desert - Pilgrim.ogg"),
    	TOWN1(  1, "Navisko", "bgm/Town - Tempskron_Stronghold.ogg"),
    	TOWN2(  2, "Ricarten", "bgm/Town 1 - Tempskron_Ricarten - When wind comes-o.ogg"),
    	VILLAGE(3, "Village", "bgm/wind loop.bgm"), // This file does not actually exist
    	FOREST( 4, "Forest", "bgm/Field - Forest - DarkWood.ogg"),
    	DUNGEON(5, "Dungeon", "bgm/Dungeon - Gloomy Heart.ogg"),
    	FILAI(  6, "Phillai", "bgm/Town 2 - Morion_Philliy - Voyage above the Clouds.ogg"),
    	SOD1(   7, "SOD1", "bgm/SOD_Stage_Play1.ogg"),
    	SOD2(   8, "SOD2", "bgm/SOD_Stage_Play1.ogg"),
    	SOD3(   9, "SOD3", "bgm/SOD_Stage_Play3.ogg"),
    	DESERT(10, "Desert", "bgm/Field - Desert - Pilgrim.ogg"),
    	ICE(   11, "Snowfield", "bgm/Ice 1.ogg");
    	LOGIN( 99, "Login", "bgm/Intro(Login) - Neo Age.ogg"),
    	CHARACTER_SELECT(100, "Character Select", "Sounds/bgm/Character Select.ogg"),
    }

### Ambient Sound Effects

Map scenes also include special ambient sounds such as rain, snow, or footsteps. These files are stored in the `wav/Ambient/` directory.

These ambient sounds are positioned at specific points in the scene and have an influence radius. When the player approaches one of these points, they hear the sound effect — for example, a crackling campfire.

The ambient sound data structure can be defined as follows:

    "Ambient": [
    	{"Position":[-1006, 170, -17835], "round":80, "AmbientNum":27},
    	{"Position":[2632, 321, -17285], "round":80, "AmbientNum":27}
    ],

`Round` is the sound's influence radius. If `Round=0`, it is a non-positional sound source that the player always hears (e.g. rain or thunder).
`AmbientNum` is the index number of the audio file.

## Background Images

Each scene in Priston Tale has at least 3 background images, used for daytime, nighttime, and dusk respectively. When a player looks into the distance, anything beyond the view distance is covered by these background images.

In practice, modern 3D games replace these with skybox textures.

Scene background images are stored in the `sky/` directory under the game root.

![sky.png](sky.png)

# Server-Side Files

Server-side files store ecological data for each region, including spawn rules, spawn points, and NPCs.

## Monster Data

`.spm` files store each region's spawn cap, spawn frequency, monster list, boss list, etc.

This is a text file editable with any text editor. It uses Korean-language markers to record the data.

## NPCs

`.spc` files store each region's NPC positions and types.

This is a binary file recording each NPC's position coordinates and facing direction, along with a path reference to the actual NPC definition file.

A scene can hold a maximum of 100 NPCs. Each NPC occupies 504 bytes, so every `.spc` file has a fixed size of 50,400 bytes. The data structure is:

    struct smTRNAS_PLAYERINFO
    {
    	int	size;   // Fixed value: 504, the size of this struct.
    	int code;   // Marks whether this NPC entry is valid (no practical meaning otherwise).

    	smCHAR_INFO	smCharInfo;

    	DWORD	dwObjectSerial;

    	int	x,y,z;      // NPC position on the world map.
    	int ax,ay,az;   // NPC facing direction (rotation around x/y/z axes). These integers are scaled by 256.
    	int state;
    };

`smCHAR_INFO` is another data structure that stores the character's model, script, attack, defense, recovery, elemental resistance, and many other attributes.

## Spawn Points (StartPoint)

`.spp` files store each region's spawn point data.

This is a binary file recording each spawn point's X/Z coordinates on the map, with a 0/1 flag indicating whether the point is active.

Each map has at most 200 spawn points. Each point is stored as 3 integers (12 bytes total):

    struct STG_START_POINT {
      int state;
      int x,z;
    };

`state` marks whether the point is in use (0 = unused, 1 = in use).
`x,z` record the spawn point's coordinates on the world map.

Since the number of spawn points and the data structure are fixed, every `.spp` file is exactly 2,400 bytes.

## JSON Format

By interpreting the server data, a JSON format was designed to redefine each region's data structure. Below is the server-side data for `fore-3` as an example:

    {
    	"NPC":[
    		{"Location":[-16503, 298, -6925], "Angle":[0, 5658, 0], "Script":"data/npc/tmcave-keeper.groovy"},
    		{"Location":[-13783, 226, -8916], "Angle":[0, 5363, 0], "Script":"data/npc/acasia-store.groovy"}],
    	"Creature":{
    		"LimitMax":200,
    		"OpenLimit":3,
    		"OpenInterval":31,
    		"MonsterList":[
    			{"Name":"Mushroom Spirit", "Percentage":12},
    			{"Name":"Beast Soldier", "Percentage":35},
    			{"Name":"Fire Sprite", "Percentage":10},
    			{"Name":"Green Treant", "Percentage":20},
    			{"Name":"One-Eyed Demon", "Percentage":3},
    			{"Name":"Biped Insect", "Percentage":20}],
    		"BossList":[
    			{"Name":"Super Treant", "Slave":"Zombie", "SlaveCnt":8, "OpenTime":[14, 18, 21, 23, 3]}]
    	},
    	"RespawnPoint":[[1, -10319,-10773], [1, -14100,-10584], [1, -10230,-10190], [1, -10583,-10874], [1, -10647,-10249], [1, -10414,-10086], [1, -10431,-9001], [1, -10602,-9193], [1, -10400,-9402], [1, -10135,-9148], [1, -12009,-9816], [1, -11636,-9754], [1, -11618,-9375], [1, -11985,-9512], [1, -10944,-7585], [1, -11049,-7244], [1, -11416,-7011], [1, -11323,-7315], [1, -11263,-7805], [1, -11641,-7597], [1, -13124,-10433], [1, -12901,-10261], [1, -12956,-10007], [1, -12653,-9915], [1, -14474,-9892], [1, -14476,-10215], [1, -14252,-10130], [1, -13471,-12141], [1, -13548,-11855], [1, -13801,-11978], [1, -13779,-11678], [1, -14166,-11655], [1, -14342,-11527], [1, -15052,-12184], [1, -15261,-12119], [1, -15267,-12349], [1, -15168,-13452], [1, -15299,-13222], [1, -12771,-12921], [1, -12975,-13058], [1, -13234,-13192], [1, -12855,-13296], [1, -12682,-13122], [1, -11191,-12353], [1, -11392,-12536], [1, -11696,-12649], [1, -16405,-12938], [1, -16166,-12805], [1, -16384,-12734], [1, -17131,-11962], [1, -16888,-12231], [1, -16889,-11821], [1, -16744,-11947], [1, -17273,-8896], [1, -16946,-9159], [1, -16965,-8894], [1, -16686,-9015], [1, -16214,-8745], [1, -15824,-8831], [1, -17461,-8495], [1, -13509,-6712], [1, -13139,-6766], [1, -13152,-6420], [1, -17135,-11693], [1, -13635,-9939], [1, -13395,-8451], [1, -13350,-8791], [1, -12920,-8141], [1, -13139,-8495], [1, -12684,-8453]]
    }

In practice, storing NPC coordinates and spawn point coordinates in JSON is somewhat cumbersome — binary format is more practical for these. However, server spawn configuration files are more readable in JSON or YAML.

Finally, here is a preview of all spawn points across all maps:
![Spawn Points](Atlas.png)

# Version History

## 0.1.0

- Implemented map data loading.
- Displays the selected map with background music playback.
- Shows spawn points (red squares) on the map.
- Camera automatically positions itself over the selected region.

![Screenshot](screenshoot.png)
