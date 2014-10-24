package com.mcjty.rftools.blocks.endergen;

import com.mcjty.entity.GenericEnergyHandlerTileEntity;
import com.mcjty.rftools.RFTools;
import com.mcjty.rftools.blocks.BlockTools;
import com.mcjty.rftools.blocks.teleporter.DialingDeviceTileEntity;
import com.mcjty.rftools.network.Argument;
import com.mcjty.rftools.network.PacketHandler;
import com.mcjty.rftools.network.PacketRequestIntegerFromServer;
import com.mcjty.rftools.network.PacketServerCommand;
import com.mcjty.varia.Coordinate;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EndergenicTileEntity extends GenericEnergyHandlerTileEntity {

    private static Random random = new Random();

    public static String CMD_SETDESTINATION = "setDest";

    public static final int CHARGE_IDLE = 0;
    public static final int CHARGE_HOLDING = -1;

    // The current chargingMode status.
    // CHARGE_IDLE means this entity is doing nothing.
    // A positive number means it is chargingMode up from 0 to 15. When it reaches 15 it will go back to idle unless
    // it was hit by an endergenic pearl in the mean time. In that case it goes to 'holding' state.
    // CHARGE_HOLDING means this entity is holding an endergenic pearl. Whie it does that it consumes
    // energy. If internal energy is depleted then the endergenic pearl is lost and the mode goes
    // back to idle.
    private int chargingMode = CHARGE_IDLE;

    // The current age of the pearl we're holding. Will be used to calculate bonuses
    // for powergeneration on pearls that are in the network for a longer time.
    private int currentAge = 0;

    // The location of the destination endergenic generator.
    private Coordinate destination = null;
    private int distance = 0;           // Distance between this block and destination in ticks

    // For pulse detection.
    private boolean prevIn = false;

    // Statistics for this generator.
    private int rfRemembered = 0;
    private int pearlsLaunched = 0;
    private int pearlsLost = 0;
    private int pearlsOpportunities = 0;
    private int ticks = 100;
    private int rfAverage = 0;

    // Current traveling pearls.
    private List<EndergenicPearl> pearls = new ArrayList<EndergenicPearl>();

    // This table indicates how much RF is produced when an endergenic pearl hits this block
    // at that specific chargingMode.
    private static int rfPerHit[] = new int[]{ 0, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 6400, 3200, 1600, 800, 400, 200, 100 };

    // This value indicates the chance (with 0 being no chance and 100 being 100% chance) that an
    // endergenic pearl is lost while holding it.
    public static int chanceLost = 1;

    // This value indicates how much RF is being consumed every tick to try to keep the endergenic pearl.
    public static int rfToHoldPearl = 1000;

    public EndergenicTileEntity() {
        super(1000000, 20000, 20000);
    }

    @Override
    protected void checkStateServer() {
        super.checkStateServer();

        ticks--;
        if (ticks < 0) {
            ticks = 100;
            int rf = getEnergyStored(ForgeDirection.DOWN);
            rfAverage = (rf-rfRemembered) / 100;
            rfRemembered = rf;
            pearlsLaunched = 0;
            pearlsLost = 0;
            pearlsOpportunities = 0;
        }

        handlePearls();

        int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        boolean newvalue = BlockTools.getRedstoneSignalIn(meta);
        boolean pulse = newvalue && !prevIn;
        prevIn = newvalue;
        if (pulse) {
            if (chargingMode == CHARGE_IDLE) {
                startCharging();
                return;
            } else if (chargingMode == CHARGE_HOLDING) {
                firePearl();
                return;
            }
        }

        if (chargingMode == CHARGE_IDLE) {
            // Do nothing
            return;
        }

        if (chargingMode == CHARGE_HOLDING) {
            if (random.nextInt(100) <= chanceLost) {
                // Pearl is lost.
                discardPearl();
            } else {
                // Consume energy to keep the endergenic pearl.
                int rfExtracted = extractEnergy(ForgeDirection.DOWN, rfToHoldPearl, false);
                System.out.println("Endergenic: consume energy "+rfToHoldPearl+", rfExtracted " + rfExtracted);
                if (rfExtracted < rfToHoldPearl) {
                    // Not enough energy. Pearl is lost.
                    discardPearl();

                }
            }
            return;
        }

        // Else we're charging up.
        markDirty();
        chargingMode++;
        if (chargingMode >= 16) {
            chargingMode = CHARGE_IDLE;
        }
    }

    // Handle all pearls that are currently in transit.
    private void handlePearls() {
        if (pearls.isEmpty()) {
            return;
        }
        List<EndergenicPearl> newlist = new ArrayList<EndergenicPearl>();
        for (EndergenicPearl pearl : pearls) {
            if (!pearl.handleTick(worldObj)) {
                // Keep the pearl. It has not arrived yet.
                newlist.add(pearl);
            }
        }

        // Replace the old list with the new one.
        pearls = newlist;
    }

    private void discardPearl() {
        System.out.println("Endergenic: pearl is lost");
        markDirty();
        pearlsLost++;
        chargingMode = CHARGE_IDLE;
    }

    /**
     * Get the current destination. This function checks first if that destination is
     * still valid and if not it is reset to null (i.e. the destination was removed).
     * @return
     */
    private EndergenicTileEntity getDestinationTE() {
        if (destination == null) {
            return null;
        }
        TileEntity te = worldObj.getTileEntity(destination.getX(), destination.getY(), destination.getZ());
        if (te instanceof EndergenicTileEntity) {
            return (EndergenicTileEntity) te;
        } else {
            destination = null;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            return null;
        }
    }

    public void firePearl() {
        System.out.println("Endergenic: fire pearl");
        markDirty();
        // This method assumes we're in holding mode.
        getDestinationTE();
        if (destination == null) {
            // There is no destination so the pearl is simply lost.
            discardPearl();
        } else {
            chargingMode = CHARGE_IDLE;
            pearlsLaunched++;
            pearls.add(new EndergenicPearl(distance, destination, currentAge+1));
        }
    }

    public void firePearlFromInjector() {
        markDirty();
        // This method assumes we're not in holding mode.
        getDestinationTE();
        chargingMode = CHARGE_IDLE;
        if (destination == null) {
            // There is no destination so the injected pearl is simply lost.
        } else {
            pearlsLaunched++;
            pearls.add(new EndergenicPearl(distance, destination, 0));
        }
    }

    // This generator receives a pearl. The age of the pearl is how many times the pearl has
    // already generated power.
    public void receivePearl(int age) {
        markDirty();
        if (chargingMode == CHARGE_HOLDING) {
            // If this block is already holding a pearl and it still has one then both pearls are
            // automatically lost.
            System.out.println("Endergenic: pearl collsion! Pearl is lost");
            chargingMode = CHARGE_IDLE;
        } else if (chargingMode == CHARGE_IDLE) {
            // If this block is idle and it is hit by a pearl then the pearl is lost and nothing
            // happens.
            System.out.println("Endergenic: we're idle, can't do anything with this pearl. Sorry!");
            chargingMode = CHARGE_IDLE;
        } else {
            // Otherwise we get RF and this block goes into holding mode.
            // @todo more energy for a pearl that has been around for a while
            int rf = rfPerHit[chargingMode];
            // Give a bonus for pearls that have been around a bit longer.
            int a = age*10;
            if (a > 100) {
                a = 100;
            }
            rf += rf * a / 100;     // Maximum 200% bonus. Minimum no bonus.
            receiveEnergy(ForgeDirection.DOWN, rf, false);

            float vecX = (random.nextFloat() - 0.5F) * 0.2F;
            float vecY = (random.nextFloat()) * 0.1F;
            float vecZ = (random.nextFloat() - 0.5F) * 0.2F;
            ((WorldServer)worldObj).func_147487_a("portal", xCoord + 0.5f, yCoord + 1.1f, zCoord + 0.5f, 10, vecX, vecY, vecZ, 0.3f);

            System.out.println("Endergenic: receive energy " + rf + ", pearl age " + age);
            chargingMode = CHARGE_HOLDING;
            currentAge = age;
        }
    }

    public void startCharging() {
        System.out.println("Endergenic: start charging");
        markDirty();
        chargingMode = 1;
        pearlsOpportunities++;
    }

    // Called from client side when a wrench is used.
    public void useWrench() {
        EndergenicTileEntity otherTE = RFTools.instance.clientInfo.getSelectedEndergenicTileEntity();
        if (otherTE == null) {
            // None selected. Just select this one.
            RFTools.instance.clientInfo.setSelectedEndergenicTileEntity(this);
            EndergenicTileEntity destinationTE = getDestinationTE();
            RFTools.instance.clientInfo.setDestinationEndergenicTileEntity(destinationTE);
            if (destinationTE == null) {
                RFTools.message(Minecraft.getMinecraft().thePlayer, "Select another endergenic generator as destination");
            } else {
                int distance = getDistanceInTicks();
                RFTools.message(Minecraft.getMinecraft().thePlayer, "Select another endergenic generator as destination (current distance "+distance+")");
            }
        } else if (otherTE == this) {
            // Unselect this one.
            RFTools.instance.clientInfo.setSelectedEndergenicTileEntity(null);
            RFTools.instance.clientInfo.setDestinationEndergenicTileEntity(null);
        } else {
            // Make a link.
            otherTE.setDestination(new Coordinate(xCoord, yCoord, zCoord));
            RFTools.instance.clientInfo.setSelectedEndergenicTileEntity(null);
            RFTools.instance.clientInfo.setDestinationEndergenicTileEntity(null);
            RFTools.message(Minecraft.getMinecraft().thePlayer, "Destination is set (distance "+otherTE.getDistanceInTicks()+" ticks)");
        }
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }

    public int getChargingMode() {
        return chargingMode;
    }

    public int getAverageRF() {
        return rfAverage;
    }

    public void setDestination(Coordinate destination) {
        markDirty();
        this.destination = destination;

        double d = Vec3.createVectorHelper(destination.getX(), destination.getY(), destination.getZ()).distanceTo(
                Vec3.createVectorHelper(xCoord, yCoord, zCoord));
        distance = (int) (d / 5.0f) + 1;

        if (worldObj.isRemote) {
            // We're on the client. Send change to server.
            PacketHandler.INSTANCE.sendToServer(new PacketServerCommand(xCoord, yCoord, zCoord,
                    EndergenicTileEntity.CMD_SETDESTINATION,
                    new Argument("dest", destination)));
        }
    }

    public int getDistanceInTicks() {
        return distance;
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);

        chargingMode = tagCompound.getInteger("charging");
        currentAge = tagCompound.getInteger("age");
        destination = Coordinate.readFromNBT(tagCompound, "dest");
        distance = tagCompound.getInteger("distance");
        rfAverage = tagCompound.getInteger("rfAverage");
        prevIn = tagCompound.getBoolean("prevIn");
        pearls.clear();
        NBTTagList list = tagCompound.getTagList("pearls", Constants.NBT.TAG_COMPOUND);
        for (int i = 0 ; i < list.tagCount() ; i++) {
            NBTTagCompound tc = list.getCompoundTagAt(i);
            EndergenicPearl pearl = new EndergenicPearl(tc);
            pearls.add(pearl);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);

        tagCompound.setInteger("charging", chargingMode);
        tagCompound.setInteger("age", currentAge);
        Coordinate.writeToNBT(tagCompound, "dest", destination);
        tagCompound.setInteger("distance", distance);
        tagCompound.setInteger("rfAverage", rfAverage);
        tagCompound.setBoolean("prevIn", prevIn);
        NBTTagList pearlList = new NBTTagList();
        for (EndergenicPearl pearl : pearls) {
            pearlList.appendTag(pearl.getTagCompound());
        }
        tagCompound.setTag("pearls", pearlList);
    }

    @Override
    public boolean execute(String command, Map<String, Argument> args) {
        boolean rc = super.execute(command, args);
        if (rc) {
            return true;
        }
        if (CMD_SETDESTINATION.equals(command)) {
            setDestination(args.get("dest").getCoordinate());
            return true;
        }
        return false;
    }
}
