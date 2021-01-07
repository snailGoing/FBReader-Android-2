/*
 * Copyright (C) 2007-2017 FBReader.ORG Limited <contact@fbreader.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.book;

/**
 * Read the metadata identifier info.
 *
 * Epub: <dc:identifier scheme="epub-id-1">urn:uuid:0f0896dc-21ab-4fe8-b87c-0c1138e66053</dc:identifier>
 *      Type: "epub-id-1"      Id:"urn:uuid:0f0896dc-21ab-4fe8-b87c-0c1138e66053"
 * If a text, will be created by MessageDigest base on the file bytes.
 */
public class UID {
    public final String Type;
    public final String Id;

    public UID(String type, String id) {
        Type = type;
        Id = id.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UID)) {
            return false;
        }
        final UID u = (UID) o;
        return Type.equals(u.Type) && Id.equals(u.Id);
    }

    @Override
    public int hashCode() {
        return Type.hashCode() + Id.hashCode();
    }
}
