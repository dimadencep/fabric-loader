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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.spongepowered.asm.service.MixinService;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.Type;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;

import net.fabricmc.loader.impl.game.minecraft.launchwrapper.FabricClassTransformer;
import net.fabricmc.loader.impl.game.minecraft.modlauncher.utils.AsmUtils;
import net.fabricmc.loader.impl.game.minecraft.modlauncher.utils.ModLauncherUtils;
import net.fabricmc.loader.impl.launch.FabricMixinBootstrap;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.ManifestUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

public class FabricModLauncher extends FabricLauncherBase implements ITransformationService, ILaunchPluginService {
	public static final LogCategory LOG_CATEGORY = LogCategory.create("GameProvider", "ModLauncher");
	protected Arguments arguments;
	public ITransformerLoader transformerLoader;
	public IEnvironment environment;
	private EnvType envType;
	private MinecraftGameProvider provider;

	public FabricModLauncher() {
		try {
			ModLauncherUtils.injectNamingService(new YarnNamingService());
			ModLauncherUtils.injectLaunchPlugin(this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String name() {
		return "FabricModLoader";
	}

	@Override
	public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
		provider.getEntrypointTransformer().locateEntrypoints(this, provider.gameJars);

		if ("mixin".equals(reason)) {
			return EnumSet.noneOf(Phase.class);
		}

		return isEmpty ? EnumSet.noneOf(Phase.class) : EnumSet.of(Phase.AFTER);
	}

	@Override
	public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
		throw new IllegalStateException("Outdated ModLauncher");
	}

	@Override
	public boolean processClass(Phase phase, ClassNode node, Type classType, String reason) {
		if ("mixin".equals(reason)) {
			return false;
		}

		byte[] finall = FabricClassTransformer.transform(classType.getClassName(), classType.getClassName(), AsmUtils.toBytes(node));
		if (finall == null) return false;

		AsmUtils.reset(node);
		AsmUtils.toClassNode(finall).accept(node);
		return true;
	}

	@Override
	public String getEntrypoint() {
		return provider.getEntrypoint();
	}

	@Override
	public String getTargetNamespace() {
		return "srg";
	}

	@Override
	public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
		this.transformerLoader = transformerLoader;
	}

	private void init() {
		setupUncaughtExceptionHandler();

		// configure fabric vars
		if (envType == null) {
			String side = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse(System.getProperty(SystemProperties.SIDE));
			if (side == null) throw new RuntimeException("Please specify side or use a dedicated FabricLaunchHandler!");

			envType = side.contains("client") ? EnvType.CLIENT : EnvType.SERVER;
		}

		provider = new MinecraftGameProvider();

		if (!provider.isEnabled() || !provider.locateGame(this, arguments.toArray())) {
			throw new RuntimeException("Could not locate Minecraft: provider locate failed");
		}

		Log.finishBuiltinConfig();

		provider.initialize(this);

		FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
		loader.setGameProvider(provider);
		loader.load();
		loader.freeze();

		FabricLoaderImpl.INSTANCE.loadAccessWideners();

		// Setup Mixin environment
		FabricMixinBootstrap.init(getEnvironmentType(), FabricLoaderImpl.INSTANCE);

		try {
			ModLauncherUtils.injectMixinConnector(new FabricMixinBootstrap());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void initialize(IEnvironment environment) {
		this.environment = environment;

		try {
			init();
		} catch (FormattedException e) {
			handleFormattedException(e);
		}
	}

	@Override
	public List<Resource> beginScanning(IEnvironment environment) {
		return ITransformationService.super.beginScanning(environment);
	}

	@Override
	public List<Resource> completeScan(IModuleLayerManager layerManager) {
		return ITransformationService.super.completeScan(layerManager);
	}

	@Override
	public void onLoad(IEnvironment environment, Set<String> otherServices) {
		this.environment = environment;

		arguments = new Arguments();

		try {
			arguments.parse(ModLauncherUtils.getArgs());
		} catch (Exception e) {
			Log.error(LOG_CATEGORY, "Failed to get arguments!", e);
		}

		Path assetsDir = environment.getProperty(IEnvironment.Keys.ASSETSDIR.get()).orElse(null);
		Path gameDir = environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElse(null);

		if (!arguments.containsKey("gameDir") && gameDir != null) {
			arguments.put("gameDir", gameDir.toAbsolutePath().toString());
		}

		if (getEnvironmentType() == EnvType.CLIENT && !arguments.containsKey("assetsDir") && assetsDir != null) {
			arguments.put("assetsDir", assetsDir.toAbsolutePath().toString());
		}
	}

	@Override
	public List<ITransformer> transformers() {
		return Collections.emptyList(); // TODO transform fabric?
	}

	@Override
	public void addToClassPath(Path path, String... allowedPrefixes) {
		SecureJar jar = SecureJar.from(ModLauncherUtils::getMetadataFromJar, path);

		try {
			ModLauncherUtils.addJarToLayer(IModuleLayerManager.Layer.GAME, jar);
		} catch (Exception ex) {
			try {
				ModLauncherUtils.addJarToLayer(IModuleLayerManager.Layer.PLUGIN, jar);
			} catch (Exception e) {
				Log.error(LOG_CATEGORY, "Failed to add path %s to any layer: %s", path, e);
			}
		}
	}

	@Override
	public void setAllowedPrefixes(Path path, String... prefixes) {
		// not implemented (no-op)
	}

	@Override
	public void setValidParentClassPath(Collection<Path> paths) {
		// not implemented (no-op)
	}

	@Override
	public List<Path> getClassPath() {
		List<Path> secureJars;

		try {
			secureJars = ModLauncherUtils.getLayerJars().stream().map(SecureJar::getPrimaryPath).toList();
		} catch (Exception e) {
			Log.error(LOG_CATEGORY, "Failed to get class path", e);

			secureJars = Collections.emptyList();
		}

		if (secureJars.isEmpty()) {
			return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(Paths::get).collect(Collectors.toList());
		}

		return secureJars;
	}

	@Override
	public boolean isClassLoaded(String name) {
		return MixinService.getService().getClassTracker().isClassLoaded(name);
	}

	@Override
	public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
		try {
			return getTargetClassLoader().loadClass(name);
		} catch (ClassNotFoundException ex) {
			return MixinService.getService().getClassProvider().findClass(name);
		}
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		InputStream inputStream = getTargetClassLoader().getResourceAsStream(name);

		if (inputStream == null) {
			return MixinService.getService().getResourceAsStream(name);
		}

		return inputStream;
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) {
		try {
			ClassNode classNode = MixinService.getService().getBytecodeProvider().getClassNode(name);

			if (classNode == null) {
				return null;
			}

			return AsmUtils.toBytes(classNode);
		} catch (ClassNotFoundException e) {
			Log.error(LOG_CATEGORY, "Class %s not found!", name);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	@Override
	public Manifest getManifest(Path originPath) {
		try {
			if (Files.isDirectory(originPath)) {
				return ManifestUtil.readManifest(originPath);
			} else {
				try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(originPath, false)) {
					return ManifestUtil.readManifest(jarFs.get().getRootDirectories().iterator().next());
				}
			}
		} catch (IOException e) {
			Log.warn(LOG_CATEGORY, "Error reading Manifest", e);
			return null;
		}
	}

	@Override
	public EnvType getEnvironmentType() {
		return envType;
	}

	@Override
	public boolean isDevelopment() {
		return Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));
	}
}
