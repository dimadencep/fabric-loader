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

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class AsmUtils {
	public static ClassNode toClassNode(InputStream inputStream) throws IOException {
		ClassReader reader = new ClassReader(inputStream);
		ClassNode result = new ClassNode();
		reader.accept(result, ClassReader.SKIP_FRAMES);
		return result;
	}

	public static ClassNode toClassNode(byte[] classBytes) {
		ClassReader reader = new ClassReader(classBytes);
		ClassNode result = new ClassNode();
		reader.accept(result, ClassReader.SKIP_FRAMES);
		return result;
	}

	public static byte[] toBytes(ClassNode classNode) {
		ClassWriter writer = new ClassWriter(0);
		classNode.accept(writer);
		return writer.toByteArray();
	}

	public static void reset(ClassNode classNode) {
		classNode.version = 0;
		classNode.access = 0;
		classNode.name = null;
		classNode.superName = null;
		classNode.interfaces.clear();
		classNode.signature = null;
		classNode.sourceFile = null;
		classNode.sourceDebug = null;
		classNode.outerClass = null;
		classNode.outerMethod = null;
		classNode.outerMethodDesc = null;
		classNode.innerClasses.clear();
		classNode.fields.clear();
		classNode.methods.clear();
		classNode.visibleAnnotations = null;
		classNode.invisibleAnnotations = null;
		classNode.attrs = null;
	}
}
