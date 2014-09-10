package com.mcjty.rftools.blocks.crafter;

import com.mcjty.container.GenericContainer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

public class CrafterContainer extends GenericContainer {
    private CrafterBlockTileEntity crafterBlockTileEntity;

    public CrafterContainer(EntityPlayer player, CrafterBlockTileEntity containerInventory) {
        super(CrafterContainerFactory.getInstance(), player);
        addInventory(CrafterContainerFactory.CONTAINER_INVENTORY, containerInventory);
        addInventory(CrafterContainerFactory.CONTAINER_PLAYER, player.inventory);
        generateSlots();

        crafterBlockTileEntity = containerInventory;
        crafterBlockTileEntity.setOldRF(-1);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int energyStored = crafterBlockTileEntity.getEnergyStored(ForgeDirection.DOWN);
        if (energyStored != crafterBlockTileEntity.getOldRF()) {
            crafterBlockTileEntity.setOldRF(energyStored);
            for (Object crafter : this.crafters) {
                ICrafting icrafting = (ICrafting) crafter;
                icrafting.sendProgressBarUpdate(this, 1, energyStored);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int type, int value) {
        super.updateProgressBar(type, value);
        if (type == 1) {
            crafterBlockTileEntity.setCurrentRF(value);
        }
    }

    @Override
    public void addCraftingToCrafters(ICrafting crafting) {
        super.addCraftingToCrafters(crafting);
        crafting.sendProgressBarUpdate(this, 0, 0);
    }

    @Override
    public ItemStack slotClick(int index, int button, int mode, EntityPlayer player) {
        if (CrafterContainerFactory.getInstance().isGhostSlot(index)) {
            Slot slot = getSlot(index);
            if (slot.getHasStack()) {
                slot.putStack(null);
            }
        }
        return super.slotClick(index, button, mode, player);
    }
}
