package com.mcjty.rftools.blocks.shards;

import com.mcjty.rftools.RFTools;
import com.mcjty.rftools.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

public class DimensionalBlankBlock extends Block {

    protected IIcon icon;

    public DimensionalBlankBlock() {
        super(Material.rock);
        setHardness(2.0f);
        setResistance(4.0f);
        setBlockName("dimensionalBlankBlock");
        setCreativeTab(RFTools.tabRfTools);
    }

    @Override
    public int getLightValue() {
        return 6;
    }

    public void registerBlockIcons(IIconRegister iconRegister) {
        icon = iconRegister.registerIcon(RFTools.MODID + ":dimblock_blank_stone");
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return icon;
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        return icon;
    }
}