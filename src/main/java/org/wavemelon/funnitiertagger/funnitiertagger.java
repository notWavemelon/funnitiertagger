package org.wavemelon.funnitiertagger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class funnitiertagger implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLoader.getInstance().getEnvironmentType();
    }}