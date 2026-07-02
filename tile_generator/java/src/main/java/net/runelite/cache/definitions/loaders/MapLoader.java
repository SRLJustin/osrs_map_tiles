/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.definitions.loaders;

import net.runelite.cache.definitions.MapDefinition;
import net.runelite.cache.definitions.MapDefinition.Tile;
import net.runelite.cache.io.InputStream;
import static net.runelite.cache.region.Region.X;
import static net.runelite.cache.region.Region.Y;
import static net.runelite.cache.region.Region.Z;

/**
 * Drop-in replacement for RuneLite's MapLoader that supports both the modern
 * OSRS terrain encoding (tile attribute + overlay id read as shorts) and the
 * legacy 2007-engine encoding used by early OSRS caches (attribute + overlay id
 * read as bytes).
 *
 * Format is selected by the system property {@code osrs.map.format}:
 *   "short" (default) -> modern format, byte-for-byte identical to upstream.
 *   "byte"            -> legacy 2007/early-OSRS format.
 *
 * This class shares a fully-qualified name with the upstream loader and must be
 * placed ahead of the net.runelite:cache jar on the classpath to take effect.
 */
public class MapLoader
{
	private static final boolean LEGACY =
		"byte".equalsIgnoreCase(System.getProperty("osrs.map.format", "short"));

	public MapDefinition load(int regionX, int regionY, byte[] b)
	{
		MapDefinition map = new MapDefinition();
		map.setRegionX(regionX);
		map.setRegionY(regionY);
		loadTerrain(map, b);
		return map;
	}

	private void loadTerrain(MapDefinition map, byte[] buf)
	{
		Tile[][][] tiles = map.getTiles();

		InputStream in = new InputStream(buf);

		for (int z = 0; z < Z; z++)
		{
			for (int x = 0; x < X; x++)
			{
				for (int y = 0; y < Y; y++)
				{
					Tile tile = tiles[z][x][y] = new Tile();
					while (true)
					{
						int attribute = LEGACY ? in.readUnsignedByte() : in.readUnsignedShort();
						if (attribute == 0)
						{
							break;
						}
						else if (attribute == 1)
						{
							int height = in.readUnsignedByte();
							tile.height = height;
							break;
						}
						else if (attribute <= 49)
						{
							tile.attrOpcode = attribute;
							tile.overlayId = LEGACY ? (short) (byte) in.readUnsignedByte() : in.readShort();
							tile.overlayPath = (byte) ((attribute - 2) / 4);
							tile.overlayRotation = (byte) (attribute - 2 & 3);
						}
						else if (attribute <= 81)
						{
							tile.settings = (byte) (attribute - 49);
						}
						else
						{
							tile.underlayId = (short) (attribute - 81);
						}
					}
				}
			}
		}
	}
}
