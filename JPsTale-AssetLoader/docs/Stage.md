# smStage3D

## Loading Process

### Overview

The first thing loaded is the FIELD definition. This process occurs in both the client and server code and has no configuration file.

When a player is about to enter a region, the client loads the map model. The model file is stored inside each FIELD object instance.

The model file defaults to ASE format, but after loading an ASE model once, the data is stored in an smStage3D object which the client saves directly as an `.smd` file. On subsequent loads, this saves the time spent parsing the ASE.

### Workflow

1. Check if the map model is already loaded in cache
2. Instantiate the smStage3D object
3. Set light angle, brightness, and contrast
4. Load the main map data via `AddLoaderStage( lpstg , szStageFile );`
5. Load any additional `smStgObj` objects that **may exist**. Depending on whether the animation flag `Bip` is present, a different loading function is called.
6. Load texture and material data
7. Cache the map

Detailed workflow derived from parsing the `playmain.cpp` source code.

smSTAGE3D *LoadStageFromField( sFIELD*lpField , sFIELD *lpSecondField )
{
smSTAGE3D*lpstg;
char \*szStageFile;
int cnt;
int Bip;
char szBuff[128];

szStageFile = lpField->szName;

if ( lpField==StageField[0] ) return smGameStage[0];
if ( lpField==StageField[1] ) return smGameStage[1];

lpstg = new smSTAGE3D;

lpstg->VectLight.x = smConfig.MapLightVector.x;
lpstg->VectLight.y = smConfig.MapLightVector.y;
lpstg->VectLight.z = smConfig.MapLightVector.z;

lpstg->Bright = smConfig.MapBright;
lpstg->Contrast = smConfig.MapContrast;

AddLoaderStage( lpstg , szStageFile );

if ( !lpstg->StageObject ) {
wsprintf( szBuff , "Stage Loading Error ( %s )",szStageFile );
Record_ClinetLogFile( szBuff );
delete lpstg;
return NULL;
}

for( cnt=0;cnt<lpField->StgObjCount;cnt++ ) {
Bip = lpField->GetStageObjectName( cnt , szBuff );
if ( szBuff[0] ) {
if ( Bip )
lpstg->StageObject->AddObjectFile( szBuff , szBuff );
else
lpstg->StageObject->AddObjectFile( szBuff );
}
}

if ( lpField->StgObjCount && lpD3DDevice ) {
ReadTextures();
lpstg->smMaterialGroup->CheckMatreialTextureSwap();
}

if ( lpSecondField && lpSecondField==StageField[0] ) {
if ( smGameStage[1] ) delete smGameStage[1];
smGameStage[1] = lpstg;
StageField[1] = lpField;
LoadFieldMap( 1, lpField , lpstg );
}
else {
if ( !lpSecondField || lpSecondField==StageField[1] ) {
if ( smGameStage[0] ) delete smGameStage[0];
smGameStage[0] = lpstg;
StageField[0] = lpField;
LoadFieldMap( 0, lpField , lpstg );
}
}

if ( StageField[0] ) lstrcpy( szGameStageName[0] , StageField[0]->szName );
if ( StageField[1] ) lstrcpy( szGameStageName[1] , StageField[1]->szName );

DWORD dwTime = GetCurrentTime();

if ( dwLastRecvGameServerTime && dwLastRecvGameServerTime<dwTime ) dwLastRecvGameServerTime=dwTime;
if ( dwLastRecvGameServerTime2 && dwLastRecvGameServerTime2<dwTime ) dwLastRecvGameServerTime2 = dwTime;
if ( dwLastRecvGameServerTime3 && dwLastRecvGameServerTime3<dwTime ) dwLastRecvGameServerTime3 = dwTime;
if ( dwLastRecvGameServerTime4 && dwLastRecvGameServerTime4<dwTime ) dwLastRecvGameServerTime4 = dwTime;

if ( AreaServerMode ) {
if ( lpWSockServer_Area[0] ) lpWSockServer_Area[0]->dwDeadLockTime = dwTime;
if ( lpWSockServer_Area[1] ) lpWSockServer_Area[1]->dwDeadLockTime = dwTime;
}

return lpstg;
}

### Save Process

The map model save function is in the `smlib3d/smStage3d.cpp` source file. The in-memory smSTAGE3D object is saved to an `.smd` file. This file contains only mesh and material data — no animation data.

static char \*szSMDFileHeader = "SMD Stage data Ver 0.72";

int smSTAGE3D::SaveFile( char \*szFile )
{
HANDLE hFile;
DWORD dwAcess;
int cnt,cnt2,slen;
int pFile;
// int size;

smDFILE_HEADER FileHeader;

lstrcpy( FileHeader.szHeader , szSMDFileHeader );

Head = FALSE;

if ( smMaterialGroup )
FileHeader.MatCounter = smMaterialGroup->MaterialCount;
else
FileHeader.MatCounter = 0;

pFile = sizeof( smDFILE_HEADER );// + sizeof( smPAT3D );

FileHeader.MatFilePoint = pFile;

if ( smMaterialGroup )
pFile+= smMaterialGroup->GetSaveSize();

FileHeader.First_ObjInfoPoint = pFile;

hFile = CreateFile( szFile , GENERIC_WRITE , FILE_SHARE_READ|FILE_SHARE_WRITE, NULL, OPEN_ALWAYS , FILE_ATTRIBUTE_NORMAL , NULL );
if ( hFile == INVALID_HANDLE_VALUE ) return FALSE;

WriteFile( hFile , &FileHeader , sizeof( smDFILE_HEADER ) , &dwAcess , NULL );

WriteFile( hFile , &Head , sizeof( smSTAGE3D ) , &dwAcess , NULL );

if ( smMaterialGroup ) smMaterialGroup->SaveFile( hFile );

WriteFile( hFile , Vertex , sizeof( smSTAGE_VERTEX )_nVertex , &dwAcess , NULL );
WriteFile( hFile , Face , sizeof( smSTAGE_FACE )_ nFace , &dwAcess , NULL );
WriteFile( hFile , TexLink , sizeof( smTEXLINK ) _nTexLink , &dwAcess , NULL );
if ( nLight>0 )
WriteFile( hFile , smLight , sizeof( smLIGHT3D )_ nLight , &dwAcess , NULL );

for( cnt2=0;cnt2<MAP_SIZE;cnt2++) {
for( cnt=0;cnt<MAP_SIZE;cnt++ ) {
if (StageArea[ cnt ][ cnt2 ]) {
slen = (StageArea[cnt][cnt2][0]+1) ;
WriteFile( hFile , &slen , sizeof(int) , &dwAcess , NULL );
WriteFile( hFile , StageArea[cnt][cnt2] , slen \* sizeof( WORD ), &dwAcess , NULL );
}
}
}

CloseHandle( hFile );

return TRUE;
}

The load process is the reverse:

int smSTAGE3D::LoadFile( char *szFile )
{
HANDLE hFile;
DWORD dwAcess;
int cnt,cnt2;
int size;
int slen;
int wbCnt;
smTEXLINK*lpOldTexLink;
int SubTexLink;

smDFILE_HEADER FileHeader;

hFile = CreateFile( szFile , GENERIC_READ , FILE_SHARE_READ|FILE_SHARE_WRITE, NULL, OPEN_EXISTING , FILE_ATTRIBUTE_NORMAL , NULL );

size=ReadFile( hFile , &FileHeader , sizeof( smDFILE_HEADER ) , &dwAcess , NULL );

if ( lstrcmp( FileHeader.szHeader , szSMDFileHeader )!=0 ) {
CloseHandle( hFile );
return FALSE;
}

ReadFile( hFile , &Head , sizeof( smSTAGE3D ) , &dwAcess , NULL );
lpOldTexLink = TexLink;

if ( FileHeader.MatCounter ) {
smMaterialGroup = new smMATERIAL_GROUP;
smMaterialGroup->LoadFile( hFile );
smMaterial = smMaterialGroup->smMaterial;
}

Vertex = new smSTAGE_VERTEX[ nVertex ];
ReadFile( hFile , Vertex , sizeof( smSTAGE_VERTEX ) \* nVertex , &dwAcess , NULL );

Face = new smSTAGE_FACE[ nFace ];
ReadFile( hFile , Face , sizeof( smSTAGE_FACE ) \* nFace , &dwAcess , NULL );

TexLink = new smTEXLINK[ nTexLink ];
ReadFile( hFile , TexLink , sizeof( smTEXLINK ) \* nTexLink , &dwAcess , NULL );

if ( nLight>0 ) {
smLight = new smLIGHT3D[nLight];
ReadFile( hFile , smLight , sizeof( smLIGHT3D ) \* nLight , &dwAcess , NULL );
}

SubTexLink = TexLink-lpOldTexLink;

for( cnt=0;cnt<nTexLink;cnt++) {
if ( TexLink[cnt].NextTex ) {
SubTexLink = TexLink[cnt].NextTex-lpOldTexLink;
TexLink[cnt].NextTex = TexLink + SubTexLink;
}
}
for( cnt=0;cnt<nFace;cnt++) {
if ( Face[cnt].lpTexLink ) {
SubTexLink = Face[cnt].lpTexLink-lpOldTexLink;
Face[cnt].lpTexLink = TexLink + SubTexLink;
}
}

StageObject = new smSTAGE_OBJECT;

lpwAreaBuff = new WORD[ wAreaSize ];
wbCnt = 0;

for( cnt2=0;cnt2<MAP_SIZE;cnt2++) {
for( cnt=0;cnt<MAP_SIZE;cnt++ ) {
if (StageArea[ cnt ][ cnt2 ]) {
ReadFile( hFile , &slen , sizeof(int) , &dwAcess , NULL );
StageArea[cnt][cnt2] = &lpwAreaBuff[ wbCnt ];
ReadFile( hFile , StageArea[cnt][cnt2] , slen\*sizeof(WORD) , &dwAcess , NULL );
wbCnt += slen;
}
}
}

CloseHandle( hFile );

CalcSumCount++;

return TRUE;
}

## Data Structures

### smSTAGE3D Data Structure

Note: The smStage3D class has no virtual functions, so there is no need to account for vtable memory. Only member variable definitions are listed.

Data structure:

#define ADD_TEMPFACE 2048
#define ADD_TEMPVERTEX 2048
#define MAP_SIZE 256

class smSTAGE3D {
public:
DWORD Head;

WORD *StageArea[MAP_SIZE][MAP_SIZE]; // Area face data storage
POINT*AreaList; // Area face list pointer
int AreaListCnt; // Area face count

int MemMode; // Memory management mode (internal)

DWORD SumCount; // Render checksum counter
int CalcSumCount; // Calculation counter

smSTAGE_VERTEX *Vertex; // Vertex list
smSTAGE_FACE*Face; // Face list
smTEXLINK *TexLink; // Texture link list
smLIGHT3D*smLight; // Light data

smMATERIAL_GROUP \*smMaterialGroup; // Material group

smSTAGE_OBJECT *StageObject; // Stage objects container
smMATERIAL*smMaterial; // Material list pointer

int nVertex; // Vertex count
int nFace; // Face count
int nTexLink; // Texture link count
int nLight; // Light count

int nVertColor; // Vertex color count

int Contrast; // Contrast (darkness)
int Bright; // Brightness (base light)

POINT3D VectLight; // Directional light vector

WORD \*lpwAreaBuff; // Area data buffer
int wAreaSize; // Area data buffer size
RECT StageMapRect; // Map area rect occupied by this stage
};

### smSTAGE3D Constructor

smSTAGE3D::smSTAGE3D()
{
VectLight.x = fONE;
VectLight.y = -fONE;
VectLight.z = fONE/2;

Bright = DEFAULT_BRIGHT;
Contrast = DEFAULT_CONTRAST;

Head = FALSE;

smLight = 0;
nLight = 0;
MemMode = 0;
}

smSTAGE3D::smSTAGE3D( int nv , int nf )
{
VectLight.x = fONE;
VectLight.y = -fONE;
VectLight.z = fONE/2;

Bright = DEFAULT_BRIGHT;
Contrast = DEFAULT_CONTRAST;

Head = FALSE;

smLight = 0;
nLight = 0;
MemMode = 0;

Init( nv, nf );
}

void smSTAGE3D::Init(void)
{
nLight = 0;
nTexLink = 0;
nFace = 0;
nVertex = 0;

::ZeroMemory( StageArea, sizeof(StageArea) );

AreaList = NULL;
Vertex = NULL;
Face = NULL;
TexLink = NULL;
smLight = NULL;
smMaterialGroup = NULL;
StageObject = NULL;
smMaterial = NULL;
lpwAreaBuff = NULL;
}

int smSTAGE3D::Init( int nv , int nf )
{
Vertex = new smSTAGE_VERTEX[ nv ];
Face = new smSTAGE_FACE[ nf ];
TexLink = new smTEXLINK[ nf * 4 ];
StageObject = new smSTAGE_OBJECT;

nVertex = 0;
nFace = 0;
nTexLink = 0;

nVertColor = 0;

SumCount = 0;
CalcSumCount = 0;

smMaterialGroup = 0;
lpwAreaBuff = 0;

StageMapRect.top = 0;
StageMapRect.bottom = 0;
StageMapRect.left = 0;
StageMapRect.right = 0;

clearStageArea();

return TRUE;
}

### smStgObj Data Structure

#define MAX_PATTERN 256
#define MAX_STAGEOBJ 1024

struct smSTAGE_OBJ3D {

smPAT3D *BipPattern;
smPAT3D*Pattern;
POINT3D Posi;
POINT3D Angle;

smSTAGE_OBJ3D \*LinkObj;
int sum;
int LastDrawTime;

};

class smSTAGE_OBJECT {
public:
smSTAGE_OBJ3D \*ObjectMap[MAP_SIZE][MAP_SIZE];

smSTAGE_OBJ3D mObj[ MAX_STAGEOBJ ];
int nObj;
POINT3D Camera;
POINT3D CameraAngle;

int SumCnt;
};

### smStgObj Constructor

smSTAGE_OBJECT::smSTAGE_OBJECT()
{
int x,y;
nObj = 0;

for(y=0;y<MAP_SIZE;y++) {
for(x=0;x<MAP_SIZE;x++) {
ObjectMap[x][y] = 0;
}
}

SumCnt = 0;
}

### smSTAGE_OBJECT::AddObjectFile

A function called during smSTAGE3D loading.

int smSTAGE_OBJECT::AddObjectFile( char *szFile , char*szBipFile )
{
int w,h;
smSTAGE_OBJ3D *obj;
smPAT3D*Pat;
smPAT3D \*BipPat;
int x,y,z;

if ( nObj>=MAX_STAGEOBJ ) return FALSE;

if ( szBipFile ) {
smASE_SetPhysique( 0 );
BipPat = smASE_ReadBone( szBipFile );
smASE_SetPhysique( BipPat );
if ( !BipPat ) return FALSE;
}
else {
smASE_SetPhysique( 0 );
BipPat = 0;
}

Pat = smASE_Read( szFile );
if ( !Pat ) {
if ( BipPat ) {
delete BipPat;
BipPat = 0;
}
return FALSE;
}

mObj[ nObj ].Pattern = Pat;
mObj[ nObj ].BipPattern = BipPat;

x = Pat->obj3d[0]->Tm.\_41;
y = Pat->obj3d[0]->Tm.\_43;
z = Pat->obj3d[0]->Tm.\_42;

mObj[ nObj ].Posi.x = x;
mObj[ nObj ].Posi.y = y;
mObj[ nObj ].Posi.z = z;

mObj[ nObj ].Angle.x = 0;
mObj[ nObj ].Angle.y = 0;
mObj[ nObj ].Angle.z = 0;

mObj[ nObj ].sum = 0;
mObj[ nObj ].LinkObj = 0;
mObj[ nObj ].LastDrawTime = GetCurrentTime();

w = (x>>(6+FLOATNS))&0xFF;
h = (z>>(6+FLOATNS))&0xFF;

if ( ObjectMap[w][h]==0 ) {
ObjectMap[w][h] = &mObj[nObj];
}
else {
obj = ObjectMap[w][h];
while( obj!=0 ) {
if ( obj->LinkObj==0 ) break;
obj = obj->LinkObj;
}
obj->LinkObj = &mObj[nObj];
}

nObj++;

return TRUE;
}

## Common Components

### smRead3d.h

char *GetString(char*q , char *p);
char*GetWord(char *q , char*p);
// Set working directory from file path
char *SetDirectoryFromFile( char*filename );

// Find file
char *smFindFile( char*szfile , char *FileExt=0 , DWORD*lpFileLen=0 );

// Change file extension
char *ChangeFileExt( char*filename , char \*FileExt );

// Set physique data (used for bone files)
void smASE_SetPhysique( smPAT3D *p );
// Get physique data (used for bone files)
smPAT3D*smASE_GetPhysique();

smPAT3D *smASE_ReadBone( char*file );
smPAT3D *smASE_Read( char*file , char _szModelName=0 );
smPAT3D_ smASE_TalkReadBone( char \*file );

// Merge animation data from multiple bone files
smPAT3D *smASE_MergeBone( char*szMeshFile, char \*\*FileList, int FileCnt, int ReadType = 1 );

int smMAP3D_ReadASE( char \*file );

// Read stage from ASE file
smSTAGE3D *smSTAGE3D_ReadASE( char*file , smSTAGE3D \*smStagep = NULL );

// Mesh data reload/save configuration
void smSetMeshReload( int flag , int MeshSave =0 );

#define sMATS_SCRIPT_WIND 1
#define sMATS_SCRIPT_ANIM2 2
#define sMATS_SCRIPT_ANIM4 4
#define sMATS_SCRIPT_ANIM8 8
#define sMATS_SCRIPT_ANIM16 16
#define sMATS_SCRIPT_WINDZ1 0x0020
#define sMATS_SCRIPT_WINDZ2 0x0040
#define sMATS_SCRIPT_WINDX1 0x0080
#define sMATS_SCRIPT_WINDX2 0x0100
#define sMATS_SCRIPT_WATER 0x0200
#define sMATS_SCRIPT_NOTVIEW 0x0400
#define sMATS_SCRIPT_PASS 0x0800
#define sMATS_SCRIPT_NOTPASS 0x1000
#define sMATS_SCRIPT_RENDLATTER 0x2000
#define sMATS_SCRIPT_BLINK_COLOR 0x4000
#define sMATS_SCRIPT_CHECK_ICE 0x8000
#define sMATS_SCRIPT_ORG_WATER 0x10000

### smRead3d.cpp

The full code is too complex; only SMD and SMB related portions are documented here.

char *szFileModelBip = "smb";
char*szFileModel = "smd";

// Find file
char *smFindFile( char*szfile , char *FileExt , DWORD*lpFileLen )
{
HANDLE hFindHandle;
WIN32_FIND_DATA fd;
WIN32_FIND_DATA fd2;

char \*szFileName;

if ( FileExt ) {
// Change extension then search
szFileName = ChangeFileExt( szfile , FileExt );
}
else
szFileName = szfile;

// Find the file (with changed extension)
hFindHandle = FindFirstFile( szFileName , &fd );
if ( hFindHandle==INVALID_HANDLE_VALUE ) {
FindClose( hFindHandle );
return FALSE;
}
FindClose( hFindHandle );

if ( lpFileLen ) \*lpFileLen = fd.nFileSizeLow;

// If requested file is found and no extension change was needed
if ( !FileExt ) {
return szFileName;
}

// Find the original file
hFindHandle = FindFirstFile( szfile , &fd2 );
if ( hFindHandle==INVALID_HANDLE_VALUE ) {
FindClose( hFindHandle );
return szFileName;
}

// If original file is newer than cached file, return FALSE to force reload
if ( CompareFileTime( &fd.ftLastWriteTime , &fd2.ftLastWriteTime )<0 ) {
return FALSE;
}

return szFileName;
}

////////////////////////////////////////////////////////////
////////// Load SMD/SMB file and convert to PAT3D //////////
////////////////////////////////////////////////////////////

smPAT3D *smReadModel( char*file , char *szModelName )
{
smPAT3D*pat;
int result;

pat = new smPAT3D;

result = pat->LoadFile( file , szModelName );

if ( result==FALSE ) {
delete pat;
return NULL;
}

return pat;
}

smPAT3D *smReadModel_Bip( char*file )
{
smPAT3D \*pat;
int result;

pat = new smPAT3D;

result = pat->LoadFile( file );

if ( result==FALSE ) {
delete pat;
return NULL;
}

return pat;
}

### smPAT3D

class smPAT3D {
DWORD Head;
public:
smOBJ3D \*obj3d[128];
BYTE TmSort[128]; // Animation sort order (post-calculation order)

smPAT3D \*TmParent;

smMATERIAL_GROUP \*smMaterialGroup; // Material group

int MaxFrame;
int Frame;

int SizeWidth , SizeHeight; // Max values for width and height

int nObj3d;
LPDIRECT3DTEXTURE2 \*hD3DTexture;

POINT3D Posi;
POINT3D Angle;
POINT3D CameraPosi;

int dBound; // Bounding sphere radius squared
int Bound; // Bounding sphere radius

smFRAME_POS TmFrame[OBJ_FRAME_SEARCH_MAX]; // Animation frame search table
int TmFrameCnt;

int TmLastFrame;
POINT3D TmLastAngle;
};

### Reading/Writing smPAT3D

static char \*szSMDFileHeader = "SMD Model data Ver 0.62";

// Save data to file
int smPAT3D::SaveFile( char \*szFile )
{

HANDLE hFile;
DWORD dwAcess;
int cnt;
int pFile;
int size;

smDFILE_HEADER FileHeader;
smDFILE_OBJINFO \*FileObjInfo;

lstrcpy( FileHeader.szHeader , szSMDFileHeader );

// Header info
if ( smMaterialGroup )
FileHeader.MatCounter = smMaterialGroup->MaterialCount;
else
FileHeader.MatCounter = 0;

FileHeader.ObjCounter = nObj3d;

FileObjInfo = new smDFILE_OBJINFO [ nObj3d ];

// File pointer
pFile = sizeof( smDFILE_HEADER ) + sizeof( smDFILE_OBJINFO ) \* nObj3d;// + sizeof( smPAT3D );

FileHeader.MatFilePoint = pFile;

// Material data save size
if ( smMaterialGroup )
pFile+= smMaterialGroup->GetSaveSize();

FileHeader.First_ObjInfoPoint = pFile;

FileHeader.TmFrameCounter = TmFrameCnt;
memcpy( FileHeader.TmFrame , TmFrame , sizeof( smFRAME_POS ) \* OBJ_FRAME_SEARCH_MAX );

// Calculate each object's file pointer and size
for( cnt=0;cnt<nObj3d;cnt++) {
lstrcpy( FileObjInfo[cnt].szNodeName , obj3d[cnt]->NodeName );
size = obj3d[cnt]->GetSaveSize();
FileObjInfo[cnt].ObjFilePoint = pFile;
FileObjInfo[cnt].Length = size;

pFile += size;
}

// Write to file
hFile = CreateFile( szFile , GENERIC_WRITE , FILE_SHARE_READ|FILE_SHARE_WRITE, NULL, OPEN_ALWAYS , FILE_ATTRIBUTE_NORMAL , NULL );
if ( hFile == INVALID_HANDLE_VALUE ) return FALSE;

// Write header
WriteFile( hFile , &FileHeader , sizeof( smDFILE_HEADER ) , &dwAcess , NULL );
WriteFile( hFile , FileObjInfo , sizeof( smDFILE_OBJINFO ) \* nObj3d, &dwAcess , NULL );
//WriteFile( hFile , &Head , sizeof( smPAT3D ), &dwAcess , NULL );

// Write material data
if ( smMaterialGroup ) smMaterialGroup->SaveFile( hFile );

// Write object data
for( cnt=0;cnt<nObj3d;cnt++) {
size = obj3d[cnt]->SaveFile( hFile );
if ( !size ) { // Error!
CloseHandle( hFile );
delete FileObjInfo;
return FALSE;
}
}

// Close file handle
CloseHandle( hFile );
delete FileObjInfo;

return TRUE;
}

// Load data from file
int smPAT3D::LoadFile( char *szFile , char*szNodeName )
{
HANDLE hFile;
DWORD dwAcess;
int cnt;
int size;
smOBJ3D *obj;
smPAT3D*BipPat;

smDFILE_HEADER FileHeader;
smDFILE_OBJINFO \*FileObjInfo;

Init();

BipPat = smASE_GetPhysique();

hFile = CreateFile( szFile , GENERIC_READ , FILE_SHARE_READ|FILE_SHARE_WRITE, NULL, OPEN_EXISTING , FILE_ATTRIBUTE_NORMAL , NULL );

// Read group header
size=ReadFile( hFile , &FileHeader , sizeof( smDFILE_HEADER ) , &dwAcess , NULL );

// Header mismatch (version mismatch)
if ( lstrcmp( FileHeader.szHeader , szSMDFileHeader )!=0 ) {
// Close file handle
CloseHandle( hFile );
return FALSE;
}

FileObjInfo = new smDFILE_OBJINFO [ FileHeader.ObjCounter ];
size=ReadFile( hFile , FileObjInfo , sizeof( smDFILE_OBJINFO ) \* FileHeader.ObjCounter, &dwAcess , NULL );

TmFrameCnt = FileHeader.TmFrameCounter;
memcpy( TmFrame , FileHeader.TmFrame , sizeof( smFRAME_POS ) \* OBJ_FRAME_SEARCH_MAX );

// Load materials
if ( FileHeader.MatCounter ) {
smMaterialGroup = new smMATERIAL_GROUP;
smMaterialGroup->LoadFile( hFile );
}

if ( szNodeName ) {
// Load only one specific object
for(cnt=0;cnt<FileHeader.ObjCounter;cnt++) {
if ( lstrcmpi( szNodeName , FileObjInfo[cnt].szNodeName )==0 ) {
obj = new smOBJ3D;
if ( obj ) {
SetFilePointer( hFile , FileObjInfo[cnt].ObjFilePoint , NULL , FILE_BEGIN );
obj->LoadFile( hFile , BipPat );
AddObject( obj );
}
break;
}
}
}
else {
// Load all objects
for(cnt=0;cnt<FileHeader.ObjCounter;cnt++) {
obj = new smOBJ3D;
if ( obj ) {
obj->LoadFile( hFile , BipPat );
AddObject( obj );
}
}
LinkObject();
}

TmParent = BipPat;

// Close file handle
CloseHandle( hFile );
delete FileObjInfo;

return TRUE;
}

### smOBJ3D

#define OBJ_FRAME_SEARCH_MAX 32
#define OBJ_HEAD_TYPE_NEW_NORMAL 0x80000000

// Vertex info for rendering
class smPOINT3D {
public:

smVERTEX \*pVertex; // Link pointer

int rx , ry , rz; // Rotated coordinates
int wx , wy , wz; // World coordinates
int sx , sy , sz;
int ox , oy , oz;

int X,Y,Z; // Screen transform coordinates
int x2d,y2d; // 2D coordinates after render
BYTE Clip2d[4]; // Clipping vertex flags

smPOINT3D();
smPOINT3D( smVERTEX \*pv );
~smPOINT3D();

void SetTo( smVERTEX *pv );
void xform2d();
void GlobalRotate(int*trig);
// Local coordinate movement
void Move(int dx, int dy, int dz);
// Screen coordinate transform
void GlobalXform();

};

// TM position info
struct smFRAME_POS {
int StartFrame;
int EndFrame;
int PosNum;
int PosCnt;
};

struct smDFILE_HEADER {
char szHeader[24];
int ObjCounter;
int MatCounter;
int MatFilePoint;
int First_ObjInfoPoint;
int TmFrameCounter;
smFRAME_POS TmFrame[OBJ_FRAME_SEARCH_MAX]; // Animation frame search table
};

struct smDFILE_OBJINFO {
char szNodeName[32];
int Length;
int ObjFilePoint;
};

struct SMotionStEndInfo
{
DWORD StartFrame;
DWORD EndFrame;
};

class smOBJ3D {
DWORD Head;
public:

smVERTEX *Vertex; // Vertices
smFACE*Face; // Faces
smTEXLINK \*TexLink; // Texture link list

smOBJ3D \*\*Physique; // Physique object for each vertex

smVERTEX ZeroVertex; // Object origin vertex

int maxZ,minZ;
int maxY,minY;
int maxX,minX;

int dBound; // Bounding sphere radius squared
int Bound; // Bounding sphere radius

int MaxVertex;
int MaxFace;

int nVertex;
int nFace;

int nTexLink;

int ColorEffect; // Color effect usage flag
DWORD ClipStates; // Clip state (per-clip usage flag)

POINT3D Posi;
POINT3D CameraPosi;
POINT3D Angle;
int Trig[8];

// Animation info
char NodeName[32]; // Object node name
char NodeParent[32]; // Parent object name
smOBJ3D \*pParent; // Parent object pointer

smMATRIX Tm; // Base TM matrix
smMATRIX TmInvert; // Inverse of Tm
//smMATRIX TmResult; // Animation matrix
smFMATRIX TmResult; // Animation matrix
smMATRIX TmRotate; // Base rotation matrix

smMATRIX mWorld; // World coordinate transform matrix
smMATRIX mLocal; // Local coordinate transform matrix

int lFrame; // Last frame

float qx,qy,qz,qw; // Rotation quaternion
int sx,sy,sz; // Scale coordinates
int px,py,pz; // Position coordinates

smTM_ROT *TmRot; // Per-frame rotation animation
smTM_POS*TmPos; // Per-frame position animation
smTM_SCALE \*TmScale; // Per-frame scale animation

smFMATRIX \*TmPrevRot; // Per-frame animation matrix

int TmRotCnt;
int TmPosCnt;
int TmScaleCnt;

// TM frame search (speeds up lookup when frame count is large)
smFRAME_POS TmRotFrame[OBJ_FRAME_SEARCH_MAX]; // ROT frame [frame, range]
smFRAME_POS TmPosFrame[OBJ_FRAME_SEARCH_MAX]; // POS frame [frame, range]
smFRAME_POS TmScaleFrame[OBJ_FRAME_SEARCH_MAX]; // SCALE frame [frame, range]
int TmFrameCnt; // TM frame counter (total)
};

### Reading/Writing smOBJ3D

// Calculate the save size
int smOBJ3D::GetSaveSize()
{
int size;

size = sizeof( smOBJ3D );
size += sizeof( smVERTEX ) _nVertex;
size += sizeof( smFACE )_ nFace;
size += sizeof( smTEXLINK ) \* nTexLink;

size += sizeof( smTM_ROT ) _TmRotCnt;
size += sizeof( smTM_POS )_ TmPosCnt;
size += sizeof( smTM_SCALE ) _TmScaleCnt;
size += sizeof( smMATRIX )_ TmRotCnt;

if ( Physique )
size += 32 \* nVertex;

return size;
}

// Save to file
int smOBJ3D::SaveFile( HANDLE hFile )
{
DWORD dwAcess;
char szBuff[64];
int cnt;
int size;

Head = 0x41424344;

//######################################################################################
// New normal format flag
Head |= OBJ_HEAD_TYPE_NEW_NORMAL;
//######################################################################################

size = WriteFile( hFile , &Head , sizeof( smOBJ3D ) , &dwAcess , NULL );
size+= WriteFile( hFile , Vertex , sizeof( smVERTEX ) _nVertex , &dwAcess , NULL );
size+= WriteFile( hFile , Face , sizeof( smFACE )_ nFace , &dwAcess , NULL );
size+= WriteFile( hFile , TexLink , sizeof( smTEXLINK ) \* nTexLink , &dwAcess , NULL );

size+= WriteFile( hFile , TmRot , sizeof( smTM_ROT ) _TmRotCnt , &dwAcess , NULL );
size+= WriteFile( hFile , TmPos , sizeof( smTM_POS )_ TmPosCnt , &dwAcess , NULL );
size+= WriteFile( hFile , TmScale , sizeof( smTM_SCALE ) _TmScaleCnt, &dwAcess , NULL );
size+= WriteFile( hFile , TmPrevRot, sizeof( smMATRIX )_ TmRotCnt , &dwAcess , NULL );

if ( Physique ) {
for( cnt=0; cnt<nVertex; cnt++ ) {
ZeroMemory( szBuff , 32 );
lstrcpy( szBuff , Physique[cnt]->NodeName );
size+= WriteFile( hFile , szBuff, 32 , &dwAcess , NULL );
}
}

return size;
}
// Load data from file
int smOBJ3D::LoadFile( HANDLE hFile , smPAT3D *PatPhysique )
{
DWORD dwAcess;
char*szBuff;
int cnt;
int len;
smTEXLINK \*lpOldTexLink;
int SubTexLink;

len=ReadFile( hFile , &Head , sizeof( smOBJ3D ) , &dwAcess , NULL );

lpOldTexLink = TexLink;

// Allocate new memory
Vertex = new smVERTEX[ nVertex ];
len+= ReadFile( hFile , Vertex , sizeof( smVERTEX ) \* nVertex , &dwAcess , NULL );

Face = new smFACE[ nFace ];
len+= ReadFile( hFile , Face , sizeof( smFACE ) \* nFace , &dwAcess , NULL );

TexLink = new smTEXLINK[ nTexLink ];
len+= ReadFile( hFile , TexLink , sizeof( smTEXLINK ) \* nTexLink , &dwAcess , NULL );

TmRot = new smTM_ROT[ TmRotCnt ];
len+= ReadFile( hFile , TmRot , sizeof( smTM_ROT ) \* TmRotCnt , &dwAcess , NULL );

TmPos = new smTM_POS[ TmPosCnt ];
len+= ReadFile( hFile , TmPos , sizeof( smTM_POS ) \* TmPosCnt , &dwAcess , NULL );

TmScale = new smTM_SCALE[ TmScaleCnt ];
len+= ReadFile( hFile , TmScale , sizeof( smTM_SCALE ) \* TmScaleCnt , &dwAcess , NULL );

//######################################################################################
// New normal format
TmPrevRot = new smFMATRIX[ TmRotCnt ];
len+= ReadFile( hFile , TmPrevRot , sizeof( smFMATRIX ) \* TmRotCnt , &dwAcess , NULL );
//######################################################################################

// Texture link pointers are stored as absolute addresses; rebase them
SubTexLink = TexLink-lpOldTexLink;

for( cnt=0;cnt<nTexLink;cnt++) {
if ( TexLink[cnt].NextTex ) {
SubTexLink = TexLink[cnt].NextTex-lpOldTexLink;
TexLink[cnt].NextTex = TexLink + SubTexLink;
}
}

for( cnt=0;cnt<nFace;cnt++) {
if ( Face[cnt].lpTexLink ) {
SubTexLink = Face[cnt].lpTexLink-lpOldTexLink;
Face[cnt].lpTexLink = TexLink + SubTexLink;
}
}

if ( Physique && PatPhysique ) {
Physique = new smOBJ3D _[ nVertex ];
szBuff = new char[ nVertex_ 32 ];
len+= ReadFile( hFile , szBuff , nVertex \* 32 , &dwAcess , NULL );

for( cnt=0; cnt<nVertex ; cnt++ ) {
Physique[cnt] = PatPhysique->GetObjectFromName( szBuff+cnt\*32 );
}

delete szBuff;
}

return len;
}

### smType.h

#define MAP_SIZE 256
#define MAP_CLIP 0xFF //(AND 0xFF)
#define MAX_CELL 4096
#define MAPTEXTURE_SIZE 64
#define RAYCLIP_ANGLE ANGLE_45+ANGLE_1
//#define RAYCLIP_ANGLE 260
#define RAYCLIP_ADD 5

#define ANGCLIP ANGLE_MASK
#define CLIP_OUT -32767
#define SMFLAG_NONE 0xFFFFFFFF
/_
#define fONE 512
#define FLOATNS 9
#define FLOATDS 7
_/

///////////////////// Fixed-point constants //////////////////
#define fONE 256
#define FLOATNS 8
#define FLOATDS 8

// MATRIX 16-bit fixed-point
#define wfONE 32768
#define wFLOATS 15
#define wSHIFT_FLOAT (wFLOATS-FLOATNS)

///////////////////// Map size related values ///////////////////
#define SizeMAPCELL 64 // Size of 1 map cell in pixels
#define ShiftMAPCELL_MULT 6 // Map cell size shift (multiply)
#define ShiftMAPCELL_DIV 6 // Map cell size shift (divide)

#define SHIFT_MAPHEIGHT (ShiftMAPCELL_MULT-3)
#define AND_SizeMAPCELL (SizeMAPCELL-1)
#define OR_SizeMAPCELL (0xFFFFFFFF-AND_SizeMAPCELL)

//////////////////// Render default brightness values ////////////////////
#define DEFAULT_CONTRAST 300
#define DEFAULT_BRIGHT 160

//////////////// smRENDER clipping status flags (ClipStatus) //////////////////
#define SMCLIP_NEARZ 0x00000001
#define SMCLIP_FARZ 0x00000002
#define SMCLIP_LEFT 0x00000004
#define SMCLIP_RIGHT 0x00000008
#define SMCLIP_TOP 0x00000010
#define SMCLIP_BOTTOM 0x00000020
#define SMCLIP_TEXTURE 0x00000040
#define SMCLIP_DISPLAYOUT 0x00000080
#define SMCLIP_DISPLAYIN 0x00000100

#define SMCLIP_VIEWPORT ( SMCLIP_NEARZ | SMCLIP_FARZ | SMCLIP_LEFT | SMCLIP_RIGHT | SMCLIP_TOP | SMCLIP_BOTTOM | SMCLIP_DISPLAYOUT )

////////////////// RGBA order //////////////////////
#define SMC_A 3
#define SMC_R 2
#define SMC_G 1
#define SMC_B 0

//######################################################################################
// Server/client texture limit configuration

#include "..\\nettype.hpp"
#ifdef \_W_SERVER
#define MAX_TEXTURE 8000
#else
#define MAX_TEXTURE 5000
#endif
//######################################################################################

// Fixed-point matrix
struct smMATRIX {
int \_11,\_12, \_13,\_14;
int \_21,\_22, \_23,\_24;
int \_31,\_32, \_33,\_34;
int \_41,\_42, \_43,\_44;
};

// Fixed-point matrix (variant 2)
struct smEMATRIX {
int \_11,\_12, \_13,\_14;
int \_21,\_22, \_23,\_24;
int \_31,\_32, \_33,\_34;
int \_41,\_42, \_43,\_44;
};

// Double-precision floating-point matrix
struct smDMATRIX {
double \_11,\_12, \_13,\_14;
double \_21,\_22, \_23,\_24;
double \_31,\_32, \_33,\_34;
double \_41,\_42, \_43,\_44;
};

// Float-precision matrix
struct smFMATRIX {
float \_11,\_12, \_13,\_14;
float \_21,\_22, \_23,\_24;
float \_31,\_32, \_33,\_34;
float \_41,\_42, \_43,\_44;
};

struct smRGB {
DWORD r,g,b;
};
struct smLIGHTLEVEL {
BYTE cr,cg,cb;
BYTE sr,sg,sb;
};

struct smLIGHT3D {
int type;
int x,y,z;
int Range;
short r,g,b;

};

#define smLIGHT_TYPE_NIGHT 0x00001
#define smLIGHT_TYPE_LENS 0x00002
#define smLIGHT_TYPE_PULSE2 0x00004 // Pulsing light flag
//######################################################################################
#define SMLIGHT_TYPE_OBJ 0x00008
//######################################################################################
#define smLIGHT_TYPE_DYNAMIC 0x80000

struct POINT3D {
int x,y,z;
};

struct TPOINT3D {
int x,y,z;
float u,v;
float zb;
BYTE bCol[4];
BYTE bSpe[4];
};

struct smLINE3D {
POINT3D sp;
POINT3D ep;
};

struct smTRECT {
float u0,v0;
float u1,v1;
float u2,v2;
float u3,v3;
};

struct smTPOINT {
int u,v;
};

struct smFTPOINT {
float u,v;
};

// Texture link for a triangle's UV coordinates and search pointer
struct smTEXLINK {
float u[3],v[3];
DWORD *hTexture;
smTEXLINK*NextTex;
};

struct smVERTEX{
int x,y,z;
int nx,ny,nz;
};

struct smFACE{
WORD v[4]; // a,b,c , material
smFTPOINT t[3]; // Texture coordinates

smTEXLINK \*lpTexLink; // Texture link pointer

};

struct smTM_ROT {
int frame;
float x,y,z,w;
};
struct smDTM_ROT {
int frame;
double x,y,z,w;
};

//######################################################################################
struct smTM_POS {
int frame;
float x, y, z;
};
//######################################################################################

struct smTM_SCALE {
int frame;
int x,y,z;
};

// Vertex for rendering
struct smRENDVERTEX {

// Transformed vertex
int tx,ty,tz; // Translated screen coordinates
DWORD ClipStatus; // Clipping status flags
float xy[2]; // 2D transformed coordinates
float zb; // Z Buffer value
float rhw; // rhw

//######################################################################################
int nx,ny,nz;
//######################################################################################

short sLight[4]; // Pre-calculated light buffer (RGBA)

BYTE bCol[4]; // Vertex color (RGBA)
BYTE bSpe[4]; // Vertex specular (RGBA)

void \*lpSourceVertex; // Original vertex pointer
};

// Face for rendering
struct smRENDFACE {

smRENDVERTEX *lpRendVertex[3]; // a,b,c
DWORD Matrial; // Material
smTEXLINK*lpTexLink; // Texture link pointer
DWORD ClipStatus; // Clipping status flags
smRENDFACE \*NexRendFace; // Next render face pointer

};

// Render light info
struct smRENDLIGHT {
int type;
int x,y,z;
int rx,ry,rz;
int Range;
int dRange;
int r,g,b,a;
};

// Render face list per material
struct smRENDMATRIAL {

int RegistRendList; // Whether registered in render list

// Opaque pass
DWORD MatrialCounter; // Number of rendered faces (per material)
smRENDFACE *StartFace; // First rendered face pointer
smRENDFACE*LastLinkFace; // Last linked face

// Texture clip pass
DWORD TexClip_MatrialCounter; // Number of rendered faces (per material)
smRENDFACE *TexClip_StartFace; // First rendered face pointer
smRENDFACE*TexClip_LastLinkFace; // Last linked face
};

// Texture rect
struct smTEXRECT {
float left, right;
float top, bottom;
};

// 2D image face
struct smFACE2D {
int x, y, z; // Position
int width , height; // Image size
smTEXRECT TexRect; // Texture rect
int MatNum; // Material number
int Transparency; // Transparency
int r,g,b;
};

// Stage vertex
struct smSTAGE_VERTEX {
DWORD sum; // Last calculation number
smRENDVERTEX \*lpRendVertex; // Render vertex pointer

// Base vertex
int x,y,z; // World coordinates

short sDef_Color[4]; // Default color (RGBA)

}; // Current size: 28 bytes

// Stage face
struct smSTAGE_FACE {
DWORD sum; // Last calculation number
int CalcSum; // Previous calculation number

WORD Vertex[4]; // a,b,c,Material

smTEXLINK \*lpTexLink; // Texture link pointer

short VectNormal[4]; // Cross product (Normal) ( nx, ny, nz, [0,1,0] dot product Y )
};

#define CONFIG_KEY_MONSTER_MAX 5

// Base configuration structure
struct smCONFIG {

int WinMode; // Window mode
POINT ScreenSize; // Screen resolution
DWORD ScreenColorBit; // Color bit depth

int TextureQuality; // Texture quality
int BGM_Mode; // Background music mode
int NetworkQuality; // Network quality
int WeatherSwitch; // Weather toggle

char szFile_BackGround[2][64]; // Background images
char szFile_Menu[64]; // Menu screen
char szFile_Player[64]; // Player character
POINT Posi_Player;
char szFile_Enemy[64]; // Enemy character
POINT Posi_Enemy;

char szFile_Stage[64]; // Stage file
POINT Posi_Stage; // Stage start position
char szFile_StageObject[100][64]; // Stage objects
int StageObjCnt; // Stage object count

//######################################################################################
int IsStageAniObject[100];
//######################################################################################

// Map loading brightness settings
int MapBright; // Brightness
int MapContrast; // Contrast
POINT3D MapLightVector; // Light vector

char szServerIP[32]; // Server IP
DWORD dwServerPort; // Server port
char szDataServerIP[32]; // Data server IP
DWORD dwDataServerPort; // Data server port
char szUserServerIP[32]; // User server IP
DWORD dwUserServerPort; // User server port
char szExtendServerIP[32]; // Extended server IP
DWORD dwExtendServerPort; // Extended server port

DWORD DebugMode; // Debug mode settings

char szCmdOpenMonster[CONFIG_KEY_MONSTER_MAX][32]; // Console monster names
int CmdMonsterCount;
};

#define MOTION_LIST_MAX 32
#define MOTION_INFO_MAX 512
#define MOTION_TOOL_MAX 52
#define MOTION_SKIL_MAX 8

//######################################################################################
// Motion rate depends on percentage values.
// Regular motions have 2 categories (idle) (attack). Each category sums to 100%.
// NPC motions have 1 category for idle, summing to 100%.
#define NPC_MOTION_INFO_MAX 30
#define TALK_MOTION_INFO_MAX 30

// MotionFrame values.
// For actual data usage, "MotionFrame-1" is used.
// Internally, MotionFrame is used as the max value.
#define TALK_MOTION_FILE_MAX 2
#define TALK_MOTION_FILE 0
#define FACIAL_MOTION_FILE 1
//######################################################################################

struct smMOTIONINFO {
DWORD State; // State: TRUE = active

//######################################################################################
DWORD MotionKeyWord_1;
DWORD StartFrame; // Start frame
DWORD MotionKeyWord_2;
DWORD EndFrame; // End frame
//######################################################################################

DWORD EventFrame[4]; // Event trigger frames

int ItemCodeCount; // Required item list count (0 = none, -1 = all items)
BYTE ItemCodeList[MOTION_TOOL_MAX]; // Required item code list
DWORD dwJobCodeBit; // Required job class bitmask
BYTE SkillCodeList[MOTION_SKIL_MAX]; // Required skill codes

int MapPosition; // Required map position (0 = any, 1 = specific, 2 = range)

DWORD Repeat; // Loop flag
CHAR KeyCode; // Motion start key
int MotionFrame; // Motion link file number
};

struct \_MODELGROUP {
int ModelNameCnt;
char szModelName[4][16];
};

struct smMODELINFO {

char szModelFile[64];
char szMotionFile[64];
char szSubModelFile[64];

\_MODELGROUP HighModel;
\_MODELGROUP DefaultModel;
\_MODELGROUP LowModel;

smMOTIONINFO MotionInfo[MOTION_INFO_MAX];
DWORD MotionCount;

//######################################################################################
DWORD FileTypeKeyWord;
DWORD LinkFileKeyWord;
//######################################################################################

//######################################################################################
char szLinkFile[64];
//######################################################################################

//######################################################################################
// Note: When there are 2 files (_.ini,_.in) with the same name,
// they are saved as \*.inx with different extensions.
char szTalkLinkFile[64];
char szTalkMotionFile[64];
smMOTIONINFO TalkMotionInfo[ TALK_MOTION_INFO_MAX ];
DWORD TalkMotionCount;

int NpcMotionRate[NPC_MOTION_INFO_MAX];
int NpcMotionRateCnt[ 100 ];

int TalkMotionRate[ TALK_MOTION_INFO_MAX ];
int TalkMotionRateCnt[ TALK_MOTION_FILE_MAX ][ 100 ];
//######################################################################################
};

extern BYTE VRKeyBuff[256];

struct smTEXPOINT {
float u,v;
DWORD hTexture;
};

struct smFCOLOR {
float r,g,b;
};

// Texture map rendering coordinate attributes
#define smTEXSTATE_FS_NONE 0
#define smTEXSTATE_FS_FORMX 1
#define smTEXSTATE_FS_FORMY 2
#define smTEXSTATE_FS_FORMZ 3
#define smTEXSTATE_FS_SCROLL 4
#define smTEXSTATE_FS_REFLEX 5

#define smTEXSTATE_FS_SCROLL2 6
#define smTEXSTATE_FS_SCROLL3 7
#define smTEXSTATE_FS_SCROLL4 8
#define smTEXSTATE_FS_SCROLL5 9
#define smTEXSTATE_FS_SCROLL6 10
#define smTEXSTATE_FS_SCROLL7 11
#define smTEXSTATE_FS_SCROLL8 12
#define smTEXSTATE_FS_SCROLL9 13
#define smTEXSTATE_FS_SCROLL10 14

#define smTEXSTATE_FS_SCROLLSLOW1 15
#define smTEXSTATE_FS_SCROLLSLOW2 16
#define smTEXSTATE_FS_SCROLLSLOW3 17
#define smTEXSTATE_FS_SCROLLSLOW4 18

/////////////////////////// Texture Map Handle Structure //////////////////////////
struct smTEXTUREHANDLE {
char Name[64];
char NameA[64];
LPDIRECT3DTEXTURE2 lpD3DTexture;
LPDIRECTDRAWSURFACE4 lpDDSurface;

     LPDIRECT3DTEXTURE2 lpD3DTextureLarge;  // High-res texture (swapped into system memory)

LPDIRECTDRAWSURFACE4 lpDDSSysMemory; // System memory texture

int Width , Height;
int UsedTime;

int UseCounter;

int MapOpacity; // Map opacity flag (TRUE, FALSE)

DWORD TexSwapMode; // Swap texture usage flag (TRUE / FALSE)

smTEXTUREHANDLE \*TexChild;
};

/////////////////////////// ASE-imported Material //////////////////////////
struct ASE_MATERIAL {
int Regist; // Whether registered in smMATERIAL
int RegistNum; // Registered material number

int TextureCounter; // Bitmap usage count
DWORD UseCounter; // Usage counter (removed if 0)

smFCOLOR Diffuse; // Diffuse color
float Transparency; // Transparency
float SelfIllum; // Self-illumination
DWORD TwoSide; // Two-sided rendering flag
DWORD ScriptState; // Script-assigned state

char BITMAP[8][64]; // Bitmap names

DWORD BitmapStateState[8]; // Bitmap stage state
DWORD BitmapFormState[8]; // Bitmap form state

char MAP_OPACITY[64]; // Opacity map name

float UVW_U_OFFSET[8]; // Map U offset
float UVW_V_OFFSET[8]; // Map V offset
float UVW_U_TILING[8]; // Map U tiling
float UVW_V_TILING[8]; // Map V tiling
float UVW_ANGLE[8]; // Map angle

int SubPoint; // Sub-material number
int BlendType; // Blend type
};

//////////////////////////////// Material //////////////////////////////
struct smMATERIAL {
DWORD InUse; // Material in-use flag
DWORD TextureCounter; // Texture count
smTEXTUREHANDLE \*smTexture[8]; // Texture handle list
// State
DWORD TextureStageState[8]; // Stage state
DWORD TextureFormState[8]; // Form state
int ReformTexture; // Texture reform state flag

int MapOpacity; // Map opacity flag (TRUE, FALSE)

// Rendering state
DWORD TextureType; // Texture type (static / animated)
DWORD BlendType; // Blend mode (SMMAT_BLEND_XXXX)

DWORD Shade; // Shading mode (flat / Gouraud)
DWORD TwoSide; // Two-sided flag
DWORD SerialNum; // Material serial number

smFCOLOR Diffuse; // Diffuse color
float Transparency; // Transparency
float SelfIllum; // Self-illumination

int TextureSwap; // Texture swap
int MatFrame; // Usage frame (for timing synchronization)
int TextureClip; // Swap texture clip flag (if TRUE, texture clipping is added)

// Mesh state
int UseState; // Usage state
int MeshState; // Mesh attribute state

// Mesh transform settings
int WindMeshBottom; // Wind mesh bottom transform start value

// Animated texture state
smTEXTUREHANDLE \*smAnimTexture[32]; // Animated texture handle list
DWORD AnimTexCounter; // Animated texture count
DWORD FrameMask; // Animation frame bitmask
DWORD Shift_FrameSpeed; // Frame speed shift (timer shift for calculation)
DWORD AnimationFrame; // Frame number (fixed-point value / SMTEX_AUTOANIMATION = auto)

};

//////////////// Material state value definitions ////////////////////

// TextureType usage
#define SMTEX_TYPE_MULTIMIX 0x0000
#define SMTEX_TYPE_ANIMATION 0x0001

// Auto-animation frame flag
#define SMTEX_AUTOANIMATION 0x100

// MeshState usage
// Face visibility check flag
#define SMMAT_STAT_CHECK_FACE 0x00000001

//
#define SMMAT_BLEND_NONE 0x00
#define SMMAT_BLEND_ALPHA 0x01
#define SMMAT_BLEND_COLOR 0x02
#define SMMAT_BLEND_SHADOW 0x03
#define SMMAT_BLEND_LAMP 0x04
#define SMMAT_BLEND_ADDCOLOR 0x05
#define SMMAT_BLEND_INVSHADOW 0x06

////////////////////////////////////////////////////////////////

//////////////// ASE Light Object ////////////////////
struct smASE_LIGHT {

int x,y,z;
int r,g,b;

int Size;
int Range;
int Dist;
int Type;
};

// Screen camera tracking coordinates
struct eCAMERA_TRACE {
int x,y,z; // Actual screen coordinates
int tx,ty,tz; // Target coordinates

smEMATRIX eRotMatrix; // Rotation-calculated matrix
int AngX,AngY; // Calculated angle (limited range)
};

### Materials

class smMATERIAL_GROUP {
DWORD Head;
public:
smMATERIAL \*smMaterial;
DWORD MaterialCount;

int ReformTexture; // Reformed texture flag

int MaxMaterial;

int LastSearchMaterial;
char szLastSearchName[64];
};

#define TEXFILENAME_SIZE 64

// Calculate save data size
int smMATERIAL_GROUP::GetSaveSize()
{
int size;
DWORD cnt ,tcnt;
int len,alen;

size = sizeof( smMATERIAL_GROUP );

for(cnt=0;cnt<MaterialCount;cnt++) {
size+= sizeof( smMATERIAL );
if ( smMaterial[cnt].InUse ) {
size += sizeof(int); // Texture name length stored as int
for( tcnt=0; tcnt<smMaterial[cnt].TextureCounter ; tcnt++) {
len = lstrlen( smMaterial[cnt].smTexture[tcnt]->Name )+1;
alen = lstrlen( smMaterial[cnt].smTexture[tcnt]->NameA )+1;
size += len;
size += alen;
}

    for( tcnt=0; tcnt<smMaterial[cnt].AnimTexCounter ; tcnt++) {
     len = lstrlen( smMaterial[cnt].smAnimTexture[tcnt]->Name )+1;
     alen = lstrlen( smMaterial[cnt].smAnimTexture[tcnt]->NameA )+1;
     size += len;
     size += alen;
    }

}
}
return size;
}

// Save data to file
int smMATERIAL_GROUP::SaveFile( HANDLE hFile )
{
DWORD dwAcess;
DWORD cnt ,tcnt;
int len;
int size;

size = WriteFile( hFile , &Head , sizeof( smMATERIAL_GROUP ) , &dwAcess , NULL );

for(cnt=0;cnt<MaterialCount;cnt++) {
// Save material
size+= WriteFile( hFile , &smMaterial[cnt] , sizeof( smMATERIAL ) , &dwAcess , NULL );

if ( smMaterial[cnt].InUse ) {

    // Calculate total texture name string length
    len = 0;
    for( tcnt=0; tcnt<smMaterial[cnt].TextureCounter ; tcnt++) {
     len += lstrlen( smMaterial[cnt].smTexture[tcnt]->Name )+1;
     len += lstrlen( smMaterial[cnt].smTexture[tcnt]->NameA )+1;
    }
    for( tcnt=0; tcnt<smMaterial[cnt].AnimTexCounter ; tcnt++) {
     len += lstrlen( smMaterial[cnt].smAnimTexture[tcnt]->Name )+1;
     len += lstrlen( smMaterial[cnt].smAnimTexture[tcnt]->NameA )+1;
    }
    // Write total string length first
    size+= WriteFile( hFile , &len , sizeof(int) , &dwAcess , NULL );

    // Write texture file names sequentially
    for( tcnt=0; tcnt<smMaterial[cnt].TextureCounter ; tcnt++) {
     len = lstrlen( smMaterial[cnt].smTexture[tcnt]->Name )+1;
     size+= WriteFile( hFile , smMaterial[cnt].smTexture[tcnt]->Name , len , &dwAcess , NULL );
     len = lstrlen( smMaterial[cnt].smTexture[tcnt]->NameA )+1;
     size+= WriteFile( hFile , smMaterial[cnt].smTexture[tcnt]->NameA , len , &dwAcess , NULL );
    }

    for( tcnt=0; tcnt<smMaterial[cnt].AnimTexCounter ; tcnt++) {
     len = lstrlen( smMaterial[cnt].smAnimTexture[tcnt]->Name )+1;
     size+= WriteFile( hFile , smMaterial[cnt].smAnimTexture[tcnt]->Name , len , &dwAcess , NULL );
     len = lstrlen( smMaterial[cnt].smAnimTexture[tcnt]->NameA )+1;
     size+= WriteFile( hFile , smMaterial[cnt].smAnimTexture[tcnt]->NameA , len , &dwAcess , NULL );
    }

}
}

return size;
}

// Load data from file
int smMATERIAL_GROUP::LoadFile( HANDLE hFile )
{
DWORD dwAcess;
DWORD cnt ,tcnt;
int StrLen;
int size;
char szNameBuff[4096];
char *lpNameBuff;
char*szName , \*szNameA;

// Read group header
size=ReadFile( hFile , &Head , sizeof( smMATERIAL_GROUP ) , &dwAcess , NULL );

// Allocate material memory
smMaterial = new smMATERIAL[ MaterialCount ];

for(cnt=0;cnt<MaterialCount;cnt++) {
// Read material data
size+= ReadFile( hFile , &smMaterial[cnt] , sizeof( smMATERIAL ) , &dwAcess , NULL );

if ( smMaterial[cnt].InUse ) {
// Read texture name buffer size
size+= ReadFile( hFile , &StrLen , sizeof( int ) , &dwAcess , NULL );
// Read texture name buffer data
size+= ReadFile( hFile , szNameBuff , StrLen, &dwAcess , NULL );

    lpNameBuff = szNameBuff;

    // Setup textures
    for( tcnt=0; tcnt<smMaterial[cnt].TextureCounter ; tcnt++) {
     szName = lpNameBuff;
     lpNameBuff += lstrlen( szName )+1;
     szNameA = lpNameBuff;
     lpNameBuff += lstrlen( szNameA )+1;

     if ( szNameA[0] )
      smMaterial[cnt].smTexture[tcnt] = smTexture.Add( szName , szNameA );
     else
      smMaterial[cnt].smTexture[tcnt] = smTexture.Add( szName );
    }

    // Setup animated textures
    for( tcnt=0; tcnt<smMaterial[cnt].AnimTexCounter ; tcnt++) {
     szName = lpNameBuff;
     lpNameBuff += lstrlen( szName )+1;
     szNameA = lpNameBuff;
     lpNameBuff += lstrlen( szNameA )+1;

     if ( szNameA[0] )
      smMaterial[cnt].smAnimTexture[tcnt] = smTexture.Add( szName , szNameA );
     else
      smMaterial[cnt].smAnimTexture[tcnt] = smTexture.Add( szName );
    }

}

}

return size;
}
