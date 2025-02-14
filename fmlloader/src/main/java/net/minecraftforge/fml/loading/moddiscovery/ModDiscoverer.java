/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import net.minecraftforge.fml.loading.LogMarkers;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public class ModDiscoverer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServiceLoader<IModLocator> locators;
    private final List<IModLocator> locatorList;

    public ModDiscoverer(Map<String, ?> arguments) {
        Launcher.INSTANCE.environment().computePropertyIfAbsent(Environment.Keys.MODDIRECTORYFACTORY.get(), v->ModsFolderLocator::new);
        Launcher.INSTANCE.environment().computePropertyIfAbsent(Environment.Keys.PROGRESSMESSAGE.get(), v-> StartupMessageManager.locatorConsumer().orElseGet(()-> s->{}));
        final var moduleLayerManager = Launcher.INSTANCE.environment().findModuleLayerManager().orElseThrow();
        locators = ServiceLoader.load(moduleLayerManager.getLayer(IModuleLayerManager.Layer.SERVICE).orElseThrow(), IModLocator.class);
        locatorList = ServiceLoaderUtils.streamServiceLoader(()->locators, sce->LOGGER.error("Failed to load locator list", sce)).collect(Collectors.toList());
        locatorList.forEach(l->l.initArguments(arguments));
        if (LOGGER.isDebugEnabled(LogMarkers.CORE))
        {
            LOGGER.debug(LogMarkers.CORE, "Found Mod Locators : {}", locatorList.stream()
                    .map(iModLocator -> "(" + iModLocator.name() + ":" + iModLocator.getClass().getPackage().getImplementationVersion() + ")").collect(Collectors.joining(",")));
        }
    }

    ModDiscoverer(List<IModLocator> locatorList) {
        this.locatorList = locatorList;
        this.locators = null;
    }

    public ModValidator discoverMods() {
        LOGGER.debug(LogMarkers.SCAN,"Scanning for mods and other resources to load. We know {} ways to find mods", locatorList.size());
        var loadedFiles = new ArrayList<>();
        for (IModLocator locator : locatorList) {
            LOGGER.debug(LogMarkers.SCAN,"Trying locator {}", locator);
            var modFiles = locator.scanMods();
            for (IModFile mf : modFiles) {
                LOGGER.info(LogMarkers.SCAN, "Found mod file {} of type {} with provider {}", mf.getFileName(), mf.getType(), mf.getProvider());
                StartupMessageManager.modLoaderConsumer().ifPresent(c->c.accept("Found mod file "+mf.getFileName()+" of type "+mf.getType()));
            }
            loadedFiles.addAll(modFiles);
        }
        final var modFilesMap = loadedFiles.stream()
                .map(ModFile.class::cast)
                .collect(Collectors.groupingBy(IModFile::getType));

        var validator = new ModValidator(modFilesMap);
        validator.stage1Validation();
        return validator;
    }

}
