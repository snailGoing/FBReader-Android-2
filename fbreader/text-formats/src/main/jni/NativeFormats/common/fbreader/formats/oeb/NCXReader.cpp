/*
 * Copyright (C) 2004-2017 FBReader.ORG Limited <contact@fbreader.org>
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

#include <cstdlib>

#include "NCXReader.h"
#include "../util/MiscUtil.h"
#include "../util/EntityFilesCollector.h"

NCXReader::NCXReader() : myReadState(READ_NONE), myPlayIndex(-65535) {
}

static const std::string TAG_NAVMAP = "navMap";
static const std::string TAG_NAVPOINT = "navPoint";
static const std::string TAG_NAVLABEL = "navLabel";
static const std::string TAG_CONTENT = "content";
static const std::string TAG_TEXT = "text";

/**
 * The start tag handle function.
 */
void NCXReader::startElementHandler(const char *fullTag, const char **attributes) {
	std::string tag = fullTag;
	const std::size_t index = tag.rfind(':');
	if (index != std::string::npos) {
		tag = tag.substr(index + 1);
	}
	switch (myReadState) {
		case READ_NONE:
			if (TAG_NAVMAP == tag) {
				myReadState = READ_MAP;
			}
			break;
		case READ_MAP:
			if (TAG_NAVPOINT == tag) {
				// save chapter point to stack.
				myPointStack.push_back(NavPoint(myPlayIndex++, myPointStack.size()));
				myReadState = READ_POINT;
			}
			break;
		case READ_POINT:
			if (TAG_NAVPOINT == tag) {
				// save chapter point to stack.
				myPointStack.push_back(NavPoint(myPlayIndex++, myPointStack.size()));
			} else if (TAG_NAVLABEL == tag) {
				myReadState = READ_LABEL;
			} else if (TAG_CONTENT == tag) {
				// save the chapter file name.
				const char *src = attributeValue(attributes, "src");
				if (src != 0) {
					myPointStack.back().ContentHRef = MiscUtil::decodeHtmlURL(src);
				}
			}
			break;
		case READ_LABEL:
			if (TAG_TEXT == tag) {
				myReadState = READ_TEXT;
			}
			break;
		case READ_TEXT:
			break;
	}
}

/**
 * The end tag handle function.
 */
void NCXReader::endElementHandler(const char *fullTag) {
	std::string tag = fullTag;
	const std::size_t index = tag.rfind(':');
	if (index != std::string::npos) {
		tag = tag.substr(index + 1);
	}
	switch (myReadState) {
		case READ_NONE:
			break;
		case READ_MAP:
			if (TAG_NAVMAP == tag) {
				myReadState = READ_NONE;
			}
			break;
		case READ_POINT:
			if (TAG_NAVPOINT == tag) {
				// special treatment for empty chapter name.
				if (myPointStack.back().Text.empty()) {
					myPointStack.back().Text = "...";
				}
				// save current chapter point to map.
				myNavigationMap[myPointStack.back().Order] = myPointStack.back();
				// the chapter point pops up in the stack.
				myPointStack.pop_back();
				myReadState = myPointStack.empty() ? READ_MAP : READ_POINT;
			}
		case READ_LABEL:
			if (TAG_NAVLABEL == tag) {
				myReadState = READ_POINT;
			}
			break;
		case READ_TEXT:
			if (TAG_TEXT == tag) {
				myReadState = READ_LABEL;
			}
			break;
	}
}

/**
 * Tag's content handle function.
 *
 * @param text Content's starting address.
 * @param len Content's length.
 */
void NCXReader::characterDataHandler(const char *text, std::size_t len) {
	if (myReadState == READ_TEXT) {
		myPointStack.back().Text.append(text, len);
	}
}

const std::vector<std::string> &NCXReader::externalDTDs() const {
	return EntityFilesCollector::xhtmlDTDs();
}

const std::map<int,NCXReader::NavPoint> &NCXReader::navigationMap() const {
	return myNavigationMap;
}

NCXReader::NavPoint::NavPoint() {
}

NCXReader::NavPoint::NavPoint(int order, std::size_t level) : Order(order), Level(level) {
}
