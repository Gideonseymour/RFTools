package com.mcjty.rftools.blocks.shield;

import com.mcjty.rftools.blocks.shield.filters.AbstractShieldFilter;
import com.mcjty.rftools.blocks.shield.filters.ShieldFilter;
import com.mcjty.rftools.network.PacketListFromServer;
import io.netty.buffer.ByteBuf;

import java.util.List;

public class PacketFiltersReady extends PacketListFromServer<PacketFiltersReady,ShieldFilter> {

    public PacketFiltersReady() {
    }

    public PacketFiltersReady(int x, int y, int z, String command, List<ShieldFilter> list) {
        super(x, y, z, command, list);
    }

    @Override
    protected ShieldFilter createItem(ByteBuf buf) {
        return AbstractShieldFilter.createFilter(buf);
    }
}
