# Image Encryption/Decryption Tool

When I first started researching the game's image files, I wrote a BMP image encryption/decryption tool in C++. Later I also looked at the TGA images used by the game and found they use the same encryption principle.
This tool has now been rewritten in Java and open-sourced. It can batch encrypt/decrypt BMP and TGA images used in the game.

Looking at it now, the encryption algorithm is actually very simple. If we skip the first 2 bytes of the file header and look at the bytes that follow:

    04 09 10 19 24 31 40 51 64 79 90 A9

These bytes are in hexadecimal and the pattern may not be obvious, but convert them to decimal:

     0   1   2   3   4   5   6   7   8   9  10  11  12  13
    41  38  04  09  10  19  24  31  40  51  64  79  90  A9
    	     4   9  16  25  36  49  64  81 100 121 144 169

- The first row is the byte index, starting from 0.
- The second row is the difference between the correct and incorrect file headers (hexadecimal).
- The third row is the same difference in decimal.

Starting from the third byte, each byte is offset by the square of its index (i²).

The game encrypts image files by modifying the first 2 bytes of the file header, then adding i² to each byte starting from index 2. BMP images have 14 bytes affected, TGA images have 18 bytes affected.

BMP decryption:

    buffer[0] = 0x42;
    buffer[1] = 0x4D;
    for(byte i=2; i<14; i++) {
    	buffer[i] -= (byte)(i*i);
    }

BMP encryption:

    buffer[0] = 0x41;
    buffer[1] = 0x38;
    for(byte i=2; i<14; i++) {
    	buffer[i] += (byte)(i*i);
    }

TGA decryption:

    buffer[0] = 0x00;
    buffer[1] = 0x00;
    for(byte i=2; i<18; i++) {
    	buffer[i] -= (byte)(i*i);
    }

TGA encryption:

    buffer[0] = 0x47;
    buffer[1] = 0x38;
    for(byte i=2; i<18; i++) {
    	buffer[i] += (byte)(i*i);
    }

UPDATED BY yan@2016/10/27

## Decryption Principle

The game's image file encryption works by corrupting the first 14 bytes of the BMP file header with incorrect data. Here are 2 examples:

Example 1:

Incorrect file header:

    41 38 3A 09 13 19 24 31 40 51 9A 79 90 A9 28 00 WRONG
          3A 09 13 19

Correct file header:

    42 4D 36 00 03 00 00 00 00 00 36 00 00 00 28 00 RIGHT
          36 00 03 00

Example 2:

Incorrect file header:
41 38 3C B9 14 19 24 31 40 51 9A 79 90 A9 28 00 WRONG
3C B9 14 19

Correct file header:

    42 4D 38 B0 04 00 00 00 00 00 36 00 00 00 28 00 RIGHT
          38 B0 04 00

As you can see, the incorrect header is produced by adding a fixed data sequence to the correct header:

    01 15-04-09-10-19-24-31-40-51-64-79-90-A9
          04 09 10 19

Looking more closely, out of these 14 bytes, only positions 3, 4, 5, and 6 actually vary — the rest are constant. In the BMP file format, these 4 bytes represent the actual file size.

We just need to compute the file size, restore the other byte positions, and replace the corrupted header to decrypt. For example:

    (1) File size: 6864
    (2) Hexadecimal: 1AD0
    (3) Padded: 00 00 1A D0
    (4) Reversed (little-endian): D0 1A 00 00
    (5) Reconstructed header:
    42 4D D0 1A 00 00 00 00 00 00 36 00 00 00 28 00

## C++ Code Explanation

The function below is the core logic. Its main purpose is to compute the correct file header based on the BMP file size.

When `type = false`, it decrypts; when `type = true`, it encrypts.

    /* Decrypt & Encrypt */
    void CPtBitmapDlg::encrypt(char *filename, unsigned long size, bool type)
    {
    	ofstream out;
    	out.open(filename, ios::in|ios::out|ios::binary);
    	if(out.is_open())
    	{
    		if (type == false) {
    			unsigned char header[14] = {0x42, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,	0x00, 0x00, 0x36, 0x00, 0x00, 0x00};
    			memcpy(aHeader, header, 14);
    		}
    		else
    		{
    			unsigned char header[16] = {0x41, 0x38, 0x00, 0x00, 0x00, 0x00, 0x24, 0x31,	0x40, 0x51, 0x9A, 0x79, 0x90, 0xA9};
    			memcpy(aHeader, header, 14);
    			size += 420481284;// Encrypt the actual file size; this number in hex is: 19 10 09 04, reversed: 04 09 10 19
    		}

    		unsigned char *pLen = (unsigned char *)&size;
    		memcpy(&aHeader[2], pLen, 4);// Patch the file size
    		out.write(aHeader, 14);
    		out.close();
    	}
    }

MADE BY keyi @ Tales of Pirates China (QQ Group: 13463366)

2015-04-07
