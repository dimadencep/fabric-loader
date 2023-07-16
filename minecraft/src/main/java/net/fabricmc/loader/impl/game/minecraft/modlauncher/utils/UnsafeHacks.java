/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl.game.minecraft.modlauncher.utils;

import java.lang.reflect.Field;

@SuppressWarnings({ "restriction", "sunapi" })
public class UnsafeHacks {
	private static final sun.misc.Unsafe UNSAFE;

	static {
		try {
			final Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			UNSAFE = (sun.misc.Unsafe) theUnsafe.get(null);
		} catch (IllegalAccessException | NoSuchFieldException e) {
			throw new RuntimeException("BARF!", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T getField(Field field, Object object) {
		if (object == null) {
			long offset = UNSAFE.staticFieldOffset(field);
			Object base = UNSAFE.staticFieldBase(field);
			return (T) UNSAFE.getObject(base, offset);
		} else {
			long offset = UNSAFE.objectFieldOffset(field);
			return (T) UNSAFE.getObject(object, offset);
		}
	}

	public static void setField(Field data, Object object, Object value) {
		if (object == null) {
			long offset = UNSAFE.staticFieldOffset(data);
			Object base = UNSAFE.staticFieldBase(data);
			UNSAFE.putObject(base, offset, value);
		} else {
			long offset = UNSAFE.objectFieldOffset(data);
			UNSAFE.putObject(object, offset, value);
		}
	}

	public static int getIntField(Field field, Object object) {
		if (object == null) {
			long offset = UNSAFE.staticFieldOffset(field);
			Object base = UNSAFE.staticFieldBase(field);
			return UNSAFE.getInt(base, offset);
		} else {
			long offset = UNSAFE.objectFieldOffset(field);
			return UNSAFE.getInt(object, offset);
		}
	}

	public static void setIntField(Field data, Object object, int value) {
		if (object == null) {
			long offset = UNSAFE.staticFieldOffset(data);
			Object base = UNSAFE.staticFieldBase(data);
			UNSAFE.putInt(base, offset, value);
		} else {
			long offset = UNSAFE.objectFieldOffset(data);
			UNSAFE.putInt(object, offset, value);
		}
	}
}
