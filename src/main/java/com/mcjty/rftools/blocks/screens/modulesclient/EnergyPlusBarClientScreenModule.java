package com.mcjty.rftools.blocks.screens.modulesclient;

import com.mcjty.varia.Coordinate;
import net.minecraft.nbt.NBTTagCompound;

public class EnergyPlusBarClientScreenModule extends EnergyBarClientScreenModule {

    @Override
    protected void setupCoordinateFromNBT(NBTTagCompound tagCompound, int dim, int x, int y, int z) {
        coordinate = Coordinate.INVALID;
        if (tagCompound.hasKey("monitorx")) {
            this.dim = tagCompound.getInteger("dim");
            coordinate = new Coordinate(tagCompound.getInteger("monitorx"), tagCompound.getInteger("monitory"), tagCompound.getInteger("monitorz"));
        }
    }
}
