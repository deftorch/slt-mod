package com.sltmod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NBTPersistenceTest {

    @Mock
    LevelChunk chunk;

    @Test
    public void testPersistence() throws Exception {
        ChunkPos pos = new ChunkPos(1, 1);
        when(chunk.getPos()).thenReturn(pos);
        CompoundTag tag = new CompoundTag();

        // 1. Mark as processed
        NBTHelper.markAsProcessed(chunk);

        // 2. Save to NBT (should save state)
        NBTHelper.saveToNBT(chunk, tag);

        // 3. Clear memory (simulate restart)
        Field field = NBTHelper.class.getDeclaredField("PROCESSED_CHUNKS");
        field.setAccessible(true);
        Set<?> processedChunks = (Set<?>) field.get(null);
        processedChunks.clear();
        assertFalse(NBTHelper.isProcessed(chunk), "Memory should be cleared");

        // 4. Load from NBT (should restore state)
        NBTHelper.loadFromNBT(chunk, tag);

        // 5. Verify restored
        assertTrue(NBTHelper.isProcessed(chunk), "State should be restored from NBT");
    }
}
