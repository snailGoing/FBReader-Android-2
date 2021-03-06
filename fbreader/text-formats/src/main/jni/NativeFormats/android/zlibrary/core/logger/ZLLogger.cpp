/*
 * Copyright (C) 2009-2017 FBReader.ORG Limited <contact@fbreader.org>
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

#include <android/log.h>
#include <stdio.h>

#include <ZLLogger.h>

void ZLLogger::println(const std::string &className, const std::string &message) const {
	if (DEBUG) {
		std::string m = message;
		for (std::size_t index = m.find('%'); index != std::string::npos; index = m.find('%', index + 2)) {
			m.replace(index, 1, "%%");
		}
		if (className == DEFAULT_CLASS) {
			__android_log_print(ANDROID_LOG_WARN, "ZLLogger", "%s", m.c_str());
		} else if (myRegisteredClasses.find(className) != myRegisteredClasses.end()) {
			__android_log_print(ANDROID_LOG_WARN, className.c_str(), "%s", m.c_str());
		}
	}
}

void ZLLogger::println(const std::string &className, const char *fmt, ...) const {
	if (DEBUG) {
		char *pszStr = NULL;
		if (NULL != fmt)
		{
			va_list marker;
			// init val.
			va_start(marker, fmt);
			// obtain the length of format string.
			size_t nLength = vprintf(fmt, marker) + 1;
			pszStr = new char[nLength];
			memset(pszStr, '\0', nLength);
            vsprintf(pszStr, fmt, marker);
			// reset.
			va_end(marker);
		}
		std::string strResult(pszStr);
		delete[]pszStr;

		println(className, strResult);
	}
}
