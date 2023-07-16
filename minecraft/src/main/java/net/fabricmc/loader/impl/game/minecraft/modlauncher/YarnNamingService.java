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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import cpw.mods.modlauncher.api.INameMappingService;

import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.mapping.tree.Mapped;

public class YarnNamingService implements INameMappingService {
	private static final MappingConfiguration mappingConfiguration = FabricLauncherBase.getLauncher().getMappingConfiguration();

	public static final Map<String, String> classNameMappings = new HashMap<>();
	public static final Map<String, String> methodNameMappings = new HashMap<>();
	public static final Map<String, String> fieldNameMappings = new HashMap<>();

	public static void generateMappings() {
		if (!classNameMappings.isEmpty()) {
			return;
		}

		buildNameMap(mappingConfiguration.getMappings().getClasses(), classNameMappings, clz -> {
			buildNameMap(clz.getMethods(), methodNameMappings, null);
			buildNameMap(clz.getFields(), fieldNameMappings, null);
		});
	}

	private static <M extends Mapped> void buildNameMap(Collection<M> entries, Map<String, String> target, Consumer<M> entryConsumer) {
		for (M entry : entries) {
			target.put(entry.getName("intermediary"), entry.getName(mappingConfiguration.getTargetNamespace()));

			if (entryConsumer != null) {
				entryConsumer.accept(entry);
			}
		}
	}

	@Override
	public String mappingName() {
		return "intermediary2srg";
	}

	@Override
	public String mappingVersion() {
		return "1.20.1";
	}

	@Override
	public Map.Entry<String, String> understanding() {
		return new AbstractMap.SimpleImmutableEntry<>("srg", "srg");
	}

	@Override
	public BiFunction<Domain, String, String> namingFunction() {
		return this::remap;
	}

	private String remap(Domain domain, String name) {
		// ensure the mapping tables are built
		generateMappings();

		switch (domain) {
		case CLASS -> {
			boolean dot = name.contains(".");
			String searchName = maybeReplace(dot, name, '.', '/');
			String target = classNameMappings.get(searchName);
			return target != null ? maybeReplace(dot, target, '/', '.') : name;
		}
		case METHOD -> {
			return methodNameMappings.getOrDefault(name, name);
		}
		case FIELD -> {
			return fieldNameMappings.getOrDefault(name, name);
		}
		default -> {
			return name;
		}
		}
	}

	private static String maybeReplace(boolean run, String s, char from, char to) {
		return run ? s.replace(from, to) : s;
	}
}
