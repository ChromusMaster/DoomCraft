package br.com.chromus.doomcraft.block;

import net.minecraft.util.StringRepresentable;

public enum TelevisionMode implements StringRepresentable {
    ACTIVE("active"),
    PAUSED("paused"),
    OFF("off"),
    BROKEN("broken");

    private final String serializedName;

    TelevisionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
