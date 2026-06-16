package com.emowolf.scm.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界持久化数据，记录通过仪式解锁的功能状态。
 * 存储在 overworld 的 SavedData 中，全维度共享。
 */
public class RitualUnlockData extends SavedData {

    private static final String DATA_NAME = "scm_ritual_unlocks";

    private boolean infiniteFluidUnlocked = false;
    private boolean flightUnlocked = false;
    private boolean antiMobBuffUnlocked = false;
    private boolean appleEatUnlocked = false;

    public static RitualUnlockData load(CompoundTag tag) {
        RitualUnlockData data = new RitualUnlockData();
        data.infiniteFluidUnlocked = tag.getBoolean("infiniteFluidUnlocked");
        data.flightUnlocked = tag.getBoolean("flightUnlocked");
        data.antiMobBuffUnlocked = tag.getBoolean("antiMobBuffUnlocked");
        data.appleEatUnlocked = tag.getBoolean("appleEatUnlocked");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("infiniteFluidUnlocked", infiniteFluidUnlocked);
        tag.putBoolean("flightUnlocked", flightUnlocked);
        tag.putBoolean("antiMobBuffUnlocked", antiMobBuffUnlocked);
        tag.putBoolean("appleEatUnlocked", appleEatUnlocked);
        return tag;
    }

    public boolean isInfiniteFluidUnlocked() {
        return infiniteFluidUnlocked;
    }

    public void setInfiniteFluidUnlocked(boolean unlocked) {
        this.infiniteFluidUnlocked = unlocked;
        setDirty();
    }

    public boolean isFlightUnlocked() {
        return flightUnlocked;
    }

    public void setFlightUnlocked(boolean unlocked) {
        this.flightUnlocked = unlocked;
        setDirty();
    }

    public boolean isAntiMobBuffUnlocked() {
        return antiMobBuffUnlocked;
    }

    public void setAntiMobBuffUnlocked(boolean unlocked) {
        this.antiMobBuffUnlocked = unlocked;
        setDirty();
    }

    public boolean isAppleEatUnlocked() {
        return appleEatUnlocked;
    }

    public void setAppleEatUnlocked(boolean unlocked) {
        this.appleEatUnlocked = unlocked;
        setDirty();
    }

    /**
     * 获取或创建 overworld 的 RitualUnlockData 实例。
     */
    public static RitualUnlockData get(net.minecraft.server.level.ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(RitualUnlockData::load, RitualUnlockData::new, DATA_NAME);
    }
}
