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

package net.fabricmc.loader.impl.game.minecraft.modlauncher;

import static net.fabricmc.loader.impl.game.minecraft.modlauncher.FabricModLauncher.LOG_CATEGORY;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;

import net.fabricmc.loader.impl.game.minecraft.modlauncher.utils.ModLauncherUtils;
import net.fabricmc.loader.impl.util.log.Log;

public class FabricJarMetadata implements JarMetadata {
	private final JarMetadata delegate;
	private final SecureJar secureJar;

	public FabricJarMetadata(SecureJar secureJar, Path path) {
		this.delegate = JarMetadata.from(secureJar, path);
		this.secureJar = secureJar;
	}

	@Override
	public String name() {
		try {
			for (SecureJar otherJar : ModLauncherUtils.getLayerJars()) {
				if (otherJar != this.secureJar && otherJar.getPackages().stream().anyMatch(this.secureJar.getPackages()::contains)) {
					String otherModuleName = otherJar.name();

					Log.info(LOG_CATEGORY, "Found existing module with name %s, renaming %s to match.", otherModuleName, this.delegate.name());
					return otherModuleName;
				}
			}
		} catch (Throwable var12) {
			Log.error(LOG_CATEGORY, "Exception occurred while trying to self-rename module " + this.delegate.name() + ": ", var12);
		}

		return this.delegate.name();
	}

	@Override
	public String version() {
		return this.delegate.version();
	}

	@Override
	public ModuleDescriptor descriptor() {
		return this.delegate.descriptor();
	}
}
