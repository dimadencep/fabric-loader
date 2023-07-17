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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.MixinConnectorManager;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;
import org.spongepowered.asm.mixin.connect.IMixinConnector;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.ModuleJarMetadata;
import cpw.mods.jarhandling.impl.SimpleJarMetadata;
import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.ModuleLayerHandler;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.INameMappingService;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;

import net.fabricmc.loader.impl.game.minecraft.modlauncher.FabricJarMetadata;

public class ModLauncherUtils {
	private static Field launchPluginsField;
	private static Field pluginsField;

	public static void injectLaunchPlugin(ILaunchPluginService service) throws Exception {
		if (launchPluginsField == null) {
			launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
			launchPluginsField.setAccessible(true);
		}

		LaunchPluginHandler launchPlugins = UnsafeHacks.getField(launchPluginsField, Launcher.INSTANCE);

		if (pluginsField == null) {
			pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
			pluginsField.setAccessible(true);
		}

		UnsafeHacks.<Map<String, ILaunchPluginService>>getField(pluginsField, launchPlugins).put(service.name(), service);
	}

	private static Field nameMappingServiceHandlerField;
	private static Field namingTableField;
	private static Constructor<?> decoratorConstructor;

	public static void injectNamingService(INameMappingService service) throws Exception {
		if (nameMappingServiceHandlerField == null) {
			nameMappingServiceHandlerField = Launcher.class.getDeclaredField("nameMappingServiceHandler");
			nameMappingServiceHandlerField.setAccessible(true);
		}

		Object nameMappingServiceHandler = UnsafeHacks.getField(nameMappingServiceHandlerField, Launcher.INSTANCE);

		if (namingTableField == null) {
			namingTableField = Class.forName("cpw.mods.modlauncher.NameMappingServiceHandler").getDeclaredField("namingTable");
			namingTableField.setAccessible(true);
		}

		Map<String, Object> namingTable = UnsafeHacks.getField(namingTableField, nameMappingServiceHandler);

		if (decoratorConstructor == null) {
			decoratorConstructor = Class.forName("cpw.mods.modlauncher.NameMappingServiceDecorator").getDeclaredConstructor(INameMappingService.class);
			decoratorConstructor.setAccessible(true);
		}

		namingTable.put(service.mappingName(), decoratorConstructor.newInstance(service));
	}

	private static Field platformConnectorsField;
	private static Field connectorsField;

	public static void injectMixinConnector(IMixinConnector mixinConnector) throws Exception {
		if (platformConnectorsField == null) {
			platformConnectorsField = MixinPlatformManager.class.getDeclaredField("connectors");
			platformConnectorsField.setAccessible(true);
		}

		MixinConnectorManager manager = UnsafeHacks.getField(platformConnectorsField, MixinBootstrap.getPlatform());

		if (connectorsField == null) {
			connectorsField = MixinConnectorManager.class.getDeclaredField("connectors");
			connectorsField.setAccessible(true);
		}

		UnsafeHacks.<List<IMixinConnector>>getField(connectorsField, manager).add(mixinConnector);
	}

	private static Field argumentHandlerField;
	private static Field argsField;

	public static String[] getArgs() throws Exception {
		if (argumentHandlerField == null) {
			argumentHandlerField = Launcher.class.getDeclaredField("argumentHandler");
			argumentHandlerField.setAccessible(true);
		}

		ArgumentHandler argumentHandler = UnsafeHacks.getField(argumentHandlerField, Launcher.INSTANCE);

		if (argsField == null) {
			argsField = ArgumentHandler.class.getDeclaredField("args");
			argsField.setAccessible(true);
		}

		return UnsafeHacks.getField(argsField, argumentHandler);
	}

	private static Method addToLayerMethod;

	public static void addJarToLayer(IModuleLayerManager.Layer layer, SecureJar jar) throws Exception {
		if (addToLayerMethod == null) {
			addToLayerMethod = ModuleLayerHandler.class.getDeclaredMethod("addToLayer", IModuleLayerManager.Layer.class, SecureJar.class);
			addToLayerMethod.setAccessible(true);
		}

		addToLayerMethod.invoke(Launcher.INSTANCE.findLayerManager().orElseThrow(), layer, jar);
	}

	private static Field layersField;

	public static EnumMap<IModuleLayerManager.Layer, List<?>> getLayerElements() throws Exception {
		if (layersField == null) {
			layersField = ModuleLayerHandler.class.getDeclaredField("layers");
			layersField.setAccessible(true);
		}

		return UnsafeHacks.getField(layersField, Launcher.INSTANCE.findLayerManager().orElseThrow());
	}

	private static Field jarField;

	public static List<SecureJar> getLayerJars() throws Exception {
		List<SecureJar> jars = new ArrayList<>();

		for (List<?> pathOrJarList : ModLauncherUtils.getLayerElements().values()) {
			for (Object pathOrJar : pathOrJarList) {
				if (jarField == null) {
					jarField = pathOrJar.getClass().getDeclaredField("jar");
					jarField.setAccessible(true);
				}

				SecureJar jar = (SecureJar) jarField.get(pathOrJar);

				if (jar != null) {
					jars.add(jar);
				}
			}
		}

		return jars;
	}

	public static JarMetadata getMetadataFromJar(final SecureJar jar) {
		var mi = jar.moduleDataProvider().findFile("module-info.class");
		var pkgs = jar.getPackages();

		if (mi.isPresent()) {
			return new ModuleJarMetadata(mi.get(), pkgs);
		}

		var providers = jar.getProviders();
		var fileCandidate = JarMetadata.fromFileName(jar.getPrimaryPath(), pkgs, providers);

		var autoName = jar.moduleDataProvider().getManifest().getMainAttributes().getValue("Automatic-Module-Name");

		if (autoName != null) {
			return new SimpleJarMetadata(autoName, fileCandidate.version(), pkgs, providers);
		}

		return new FabricJarMetadata(jar, fileCandidate);
	}
}
